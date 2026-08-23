package com.junchen.jingdu

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

internal data class BillingState(
    val unlocked: Boolean,
    val available: Boolean,
    val connected: Boolean,
    val price: String?,
)

internal class BillingManager(
    private val activity: Activity,
    private val onState: (BillingState) -> Unit,
    private val onMessage: (String) -> Unit,
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var unlocked = preferences.getBoolean(KEY_CACHED_PRO, false)
    private var connected = false
    private var productDetails: ProductDetails? = null
    private var offerToken: String? = null
    private var formattedPrice: String? = null

    private val billingClient = BillingClient.newBuilder(activity.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        publish()
        if (billingClient.isReady) {
            connected = true
            queryOwned(showResult = false)
            queryProduct()
        } else {
            billingClient.startConnection(this)
        }
    }

    fun close() {
        if (billingClient.isReady) billingClient.endConnection()
    }

    fun purchase() {
        if (unlocked) {
            onMessage("净读 Pro 已解锁。")
            return
        }
        val details = productDetails
        val token = offerToken
        if (!billingClient.isReady || details == null || token.isNullOrEmpty()) {
            onMessage("当前无法连接 Google Play 购买服务，请稍后重试。")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(token)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onMessage("暂时无法发起购买：${result.debugMessage}")
        }
    }

    fun restore() {
        if (!billingClient.isReady) {
            start()
            onMessage("正在连接 Google Play 并恢复购买…")
            return
        }
        queryOwned(showResult = true)
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        connected = result.responseCode == BillingClient.BillingResponseCode.OK
        publish()
        if (!connected) return
        queryOwned(showResult = false)
        queryProduct()
    }

    override fun onBillingServiceDisconnected() {
        connected = false
        publish()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty(), authoritative = false)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwned(showResult = true)
            else -> onMessage("购买未完成：${result.debugMessage}")
        }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = queryResult.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
                val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                productDetails = details
                offerToken = offer?.offerToken
                formattedPrice = offer?.formattedPrice
            } else {
                productDetails = null
                offerToken = null
                formattedPrice = null
            }
            publish()
        }
    }

    private fun queryOwned(showResult: Boolean) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val restored = processPurchases(purchases, authoritative = true)
                if (showResult) {
                    onMessage(if (restored) "已恢复净读 Pro。" else "当前 Google Play 账号没有净读 Pro 购买记录。")
                }
            } else if (showResult) {
                onMessage("恢复购买失败：${result.debugMessage}")
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>, authoritative: Boolean): Boolean {
        var ownsPro = false
        purchases.forEach { purchase ->
            if (PRODUCT_ID !in purchase.products) return@forEach
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
            ownsPro = true
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        onMessage("Pro 已解锁，但购买确认尚未完成，请保持联网后重新打开净读。")
                    }
                }
            }
        }
        if (authoritative || ownsPro) setUnlocked(ownsPro)
        return ownsPro
    }

    private fun setUnlocked(value: Boolean) {
        unlocked = value
        preferences.edit().putBoolean(KEY_CACHED_PRO, value).apply()
        publish()
    }

    private fun publish() {
        activity.runOnUiThread {
            onState(
                BillingState(
                    unlocked = unlocked,
                    available = productDetails != null && !offerToken.isNullOrEmpty(),
                    connected = connected,
                    price = formattedPrice,
                ),
            )
        }
    }

    companion object {
        const val PRODUCT_ID = "jingdu_pro_lifetime"
        private const val PREFS = "jingdu.billing.v1"
        private const val KEY_CACHED_PRO = "pro.cached"
    }
}
