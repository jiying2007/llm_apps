# HarmonyOS

Native HarmonyOS Stage/ArkUI shell for Jingdu TXT. All document business and algorithm behavior is provided by `../../core/native` through Node-API; ArkTS owns only FilePicker/charset normalization, UI/lifecycle, local preferences and Core Speech Kit integration.

Requirements: Huawei standard-system phone/tablet/2-in-1, HarmonyOS 5.0.0+; build with DevEco Studio 6.0+ and HarmonyOS SDK 6.0+. The source target is SDK 6.0.0(20).

Main flow: `DocumentViewPicker` import -> private legacy-charset decode to UTF-8 -> shared native core -> reading/search/chapters/bookmarks/repair/export -> Core Speech Kit TTS.

No network permission, account, analytics SDK, alternate business core or compatibility implementation is used.
