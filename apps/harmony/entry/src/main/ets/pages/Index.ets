import { common } from '@kit.AbilityKit';
import { picker } from '@kit.CoreFileKit';
import { BookMeta, BookStore } from '../model/BookStore';
import { ChapterItem, ReaderController, SearchHit } from '../model/ReaderController';
import { TtsController } from '../model/TtsController';

@Entry
@Component
struct Index {
  @State pageText: string = '导入本地 TXT 开始阅读。\n\n文件只复制到应用私有目录，源文件不会被修改。';
  @State status: string = '离线 · 无账号 · 无网络权限';
  @State books: BookMeta[] = [];
  @State searchHits: SearchHit[] = [];
  @State chapters: ChapterItem[] = [];
  @State bookmarks: number[] = [];
  @State panel: string = '';
  @State searchQuery: string = '';
  @State ruleFrom: string = '';
  @State ruleTo: string = '';
  @State fontSize: number = 20;
  @State night: boolean = false;
  @State busy: boolean = false;
  @State autoReading: boolean = false;
  @State ttsReading: boolean = false;
  @State cleanMode: boolean = false;

  private store?: BookStore;
  private current?: BookMeta;
  private reader: ReaderController = new ReaderController();
  private tts: TtsController = new TtsController();
  private autoTimer: number = -1;
  private sleepTimer: number = -1;
  private autoDelay: number = 6000;

  aboutToAppear(): void {
    let context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    this.store = new BookStore(context);
    this.refreshLibrary();
    if (this.books.length > 0) this.openBook(this.books[0], false);
  }

  aboutToDisappear(): void {
    this.stopAuto();
    this.cancelSleepTimer();
    this.tts.close();
    this.persistProgress();
    this.reader.close();
  }

  build() {
    Column() {
      Scroll() {
        Row({ space: 6 }) {
          Button('导入').onClick(() => this.panel = 'encoding')
          Button('书架').onClick(() => { this.refreshLibrary(); this.panel = 'library'; })
          Button('搜索').enabled(this.current !== undefined).onClick(() => this.panel = 'search')
          Button('目录').enabled(this.current !== undefined).onClick(() => { this.loadChapters(); this.panel = 'chapters'; })
          Button('书签').enabled(this.current !== undefined).onClick(() => { this.loadBookmarks(); this.panel = 'bookmarks'; })
          Button('净读').enabled(this.current !== undefined).onClick(() => this.panel = 'repair')
          Button(this.ttsReading ? '暂停朗读' : '朗读').enabled(this.current !== undefined).onClick(() => this.toggleTts())
          Button(this.autoReading ? '停止自动' : '自动').enabled(this.current !== undefined).onClick(() => this.toggleAuto())
          Button('睡眠').enabled(this.current !== undefined).onClick(() => this.panel = 'sleep')
          Button(this.night ? '日间' : '夜间').onClick(() => this.night = !this.night)
          Button('A-').onClick(() => this.fontSize = Math.max(14, this.fontSize - 2))
          Button('A+').onClick(() => this.fontSize = Math.min(36, this.fontSize + 2))
        }
        .padding({ left: 8, right: 8 })
      }
      .scrollable(ScrollDirection.Horizontal)
      .width('100%')
      .height(52)

      Text(this.status)
        .fontSize(12)
        .fontColor(this.night ? '#B8B8B8' : '#666666')
        .padding({ left: 16, right: 16, top: 6, bottom: 6 })
        .width('100%')

      if (this.panel.length > 0) {
        this.panelView()
      }

      Scroll() {
        Text(this.pageText)
          .fontSize(this.fontSize)
          .fontColor(this.night ? '#E2E2E2' : '#242424')
          .lineHeight(this.fontSize * 1.55)
          .copyOption(CopyOptions.LocalDevice)
          .width('100%')
          .padding({ left: 24, right: 24, top: 16, bottom: 64 })
      }
      .width('100%')
      .layoutWeight(1)
      .backgroundColor(this.night ? '#181818' : '#FAF8F0')

      Row({ space: 12 }) {
        Button('上一页').enabled(this.current !== undefined).layoutWeight(1).onClick(() => { this.reader.previous(); this.render(); })
        Button('下一页').enabled(this.current !== undefined).layoutWeight(1).onClick(() => { this.reader.next(); this.render(); })
      }
      .width('100%')
      .padding(10)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.night ? '#181818' : '#FAF8F0')
  }

  @Builder
  panelView() {
    Column({ space: 8 }) {
      Row() {
        Text(this.panelTitle()).fontSize(16).fontWeight(FontWeight.Medium).layoutWeight(1)
        Button('关闭').onClick(() => this.panel = '')
      }.width('100%')

      if (this.panel === 'encoding') {
        Text('选择源 TXT 编码；自动模式优先 UTF-8/BOM，否则按 GB18030 处理。').fontSize(12)
        Flex({ wrap: FlexWrap.Wrap, space: { main: 6, cross: 6 } }) {
          ForEach(BookStore.ENCODINGS, (encoding: string) => {
            Button(encoding).onClick(async () => await this.pickImport(encoding))
          }, (encoding: string) => encoding)
        }
      } else if (this.panel === 'library') {
        if (this.books.length === 0) Text('书架为空')
        ForEach(this.books, (book: BookMeta) => {
          Row() {
            Column() {
              Text(book.name).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
              Text(book.encoding).fontSize(11).fontColor('#777777')
            }.alignItems(HorizontalAlign.Start).layoutWeight(1)
            Button('打开').onClick(() => { this.openBook(book, false); this.panel = ''; })
          }.width('100%')
        }, (book: BookMeta) => book.id)
      } else if (this.panel === 'search') {
        Row({ space: 8 }) {
          TextInput({ placeholder: '全文搜索', text: this.searchQuery }).layoutWeight(1)
            .onChange((value: string) => this.searchQuery = value)
          Button('搜索').onClick(() => this.runSearch())
        }.width('100%')
        Scroll() {
          Column() {
            ForEach(this.searchHits, (hit: SearchHit) => {
              Text(hit.context).fontSize(13).padding(8).width('100%')
                .onClick(() => { this.reader.jump(hit.offset); this.render(); this.panel = ''; })
            }, (hit: SearchHit) => `${hit.offset}:${hit.context}`)
          }
        }.height(Math.min(240, Math.max(48, this.searchHits.length * 48)))
      } else if (this.panel === 'chapters') {
        Scroll() {
          Column() {
            if (this.chapters.length === 0) Text('未识别到章节标题')
            ForEach(this.chapters, (chapter: ChapterItem) => {
              Text(chapter.title).padding(8).width('100%').onClick(() => {
                this.reader.jump(chapter.offset); this.render(); this.panel = '';
              })
            }, (chapter: ChapterItem) => `${chapter.offset}:${chapter.title}`)
          }
        }.height(Math.min(280, Math.max(48, this.chapters.length * 44)))
      } else if (this.panel === 'bookmarks') {
        Button('添加当前位置').onClick(() => this.addBookmark())
        Scroll() {
          Column() {
            ForEach(this.bookmarks, (offset: number) => {
              Row() {
                Text(`位置 ${offset}`).layoutWeight(1).onClick(() => { this.reader.jump(offset); this.render(); this.panel = ''; })
                Button('删除').onClick(() => this.removeBookmark(offset))
              }.width('100%').padding(4)
            }, (offset: number) => offset.toString())
          }
        }.height(Math.min(220, Math.max(48, this.bookmarks.length * 44)))
      } else if (this.panel === 'repair') {
        TextInput({ placeholder: '查找（字面文本）', text: this.ruleFrom }).onChange((value: string) => this.ruleFrom = value)
        TextInput({ placeholder: '替换为', text: this.ruleTo }).onChange((value: string) => this.ruleTo = value)
        Row({ space: 8 }) {
          Button('添加规则').onClick(() => this.addRule())
          Button(this.cleanMode ? '原文视图' : '净读预览').onClick(() => this.toggleClean())
          Button('导出').onClick(async () => await this.exportClean())
          Button('清空规则').onClick(() => this.clearRules())
        }
        Text(`规则数：${this.ruleCount()}`).fontSize(12)
      } else if (this.panel === 'sleep') {
        Row({ space: 8 }) {
          Button('关闭').onClick(() => this.setSleepTimer(0))
          Button('15 分钟').onClick(() => this.setSleepTimer(15))
          Button('30 分钟').onClick(() => this.setSleepTimer(30))
          Button('60 分钟').onClick(() => this.setSleepTimer(60))
        }
        Text('定时结束会停止自动阅读和 TTS。').fontSize(12)
      } else if (this.panel === 'danger') {
        Button('删除当前私有副本').fontColor(Color.Red).onClick(() => this.confirmDelete())
      }

      if (this.current !== undefined && this.panel !== 'danger') {
        Button('删除私有副本…').fontColor(Color.Red).onClick(() => this.panel = 'danger')
      }
    }
    .padding(12)
    .margin({ left: 8, right: 8, bottom: 4 })
    .borderRadius(12)
    .backgroundColor(this.night ? '#282828' : '#F0EDE5')
    .width('calc(100% - 16vp)')
  }

  private panelTitle(): string {
    switch (this.panel) {
      case 'encoding': return '导入 TXT';
      case 'library': return '书架';
      case 'search': return `搜索结果 · ${this.searchHits.length}`;
      case 'chapters': return `目录 · ${this.chapters.length}`;
      case 'bookmarks': return `书签 · ${this.bookmarks.length}`;
      case 'repair': return '净读';
      case 'sleep': return '睡眠定时';
      case 'danger': return '删除';
      default: return '';
    }
  }

  private refreshLibrary(): void {
    this.books = this.store?.list() ?? [];
  }

  private async pickImport(encoding: string): Promise<void> {
    if (!this.store || this.busy) return;
    try {
      let context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      let options = new picker.DocumentSelectOptions();
      options.maxSelectNumber = 1;
      options.fileSuffixFilters = ['TXT|.txt'];
      let documentPicker = new picker.DocumentViewPicker(context);
      let uris = await documentPicker.select(options);
      if (uris.length === 0) return;
      this.busy = true;
      this.status = '正在导入…';
      let book = this.store.importUri(uris[0], encoding);
      this.refreshLibrary();
      this.openBook(book, false);
      this.panel = '';
    } catch (error) {
      this.showError(error, '导入失败');
    } finally {
      this.busy = false;
    }
  }

  private openBook(book: BookMeta, clean: boolean): void {
    if (!this.store) return;
    this.stopAuto();
    this.tts.stop('');
    this.ttsReading = false;
    let restored = this.current?.id === book.id ? this.reader.position() : book.progress;
    this.current = book;
    this.cleanMode = clean;
    try {
      let path = clean ? this.buildClean(book) : this.store.normalizedPath(book);
      this.reader.open(path, restored);
      this.render();
    } catch (error) {
      this.showError(error, '打开失败');
    }
  }

  private render(): void {
    if (!this.current || !this.store) return;
    try {
      this.pageText = this.reader.page();
      this.store.saveProgress(this.current, this.reader.position());
      let percent = this.reader.length() === 0 ? 0 : Math.min(100, Math.floor(this.reader.position() * 100 / this.reader.length()));
      this.status = `${this.current.name} · ${percent}% · ${this.current.encoding}${this.cleanMode ? ' · 净读' : ' · 原文视图'}`;
    } catch (error) {
      this.showError(error, '读取失败');
    }
  }

  private persistProgress(): void {
    if (this.current && this.store) this.store.saveProgress(this.current, this.reader.position());
  }

  private runSearch(): void {
    if (this.searchQuery.trim().length === 0) return;
    try { this.searchHits = this.reader.search(this.searchQuery); }
    catch (error) { this.showError(error, '搜索失败'); }
  }

  private loadChapters(): void {
    try { this.chapters = this.reader.chapters(); }
    catch (error) { this.showError(error, '目录失败'); }
  }

  private loadBookmarks(): void {
    if (!this.current || !this.store) return;
    this.bookmarks = this.store.bookmarks(this.current).sort((a: number, b: number): number => a - b);
  }

  private addBookmark(): void {
    if (!this.current || !this.store) return;
    let offset = this.reader.position();
    let values = this.store.bookmarks(this.current);
    if (values.indexOf(offset) < 0) values.push(offset);
    values.sort((a: number, b: number): number => a - b);
    this.store.saveBookmarks(this.current, values);
    this.bookmarks = values;
  }

  private removeBookmark(offset: number): void {
    if (!this.current || !this.store) return;
    let values = this.store.bookmarks(this.current).filter((item: number): boolean => item !== offset);
    this.store.saveBookmarks(this.current, values);
    this.bookmarks = values;
  }

  private rules(): string {
    return this.current && this.store ? this.store.rules(this.current) : '';
  }

  private ruleCount(): number {
    let value = this.rules();
    if (value.length === 0) return 0;
    return value.split('\u001e').length;
  }

  private addRule(): void {
    if (!this.current || !this.store) return;
    let from = this.ruleFrom;
    let to = this.ruleTo;
    if (from.length === 0 || from.indexOf('\u001e') >= 0 || from.indexOf('\u001f') >= 0 || to.indexOf('\u001e') >= 0 || to.indexOf('\u001f') >= 0) {
      this.toast('规则包含保留分隔符或查找为空');
      return;
    }
    let old = this.rules();
    let packed = `${old}${old.length === 0 ? '' : '\u001e'}${from}\u001f${to}`;
    this.store.saveRules(this.current, packed);
    this.ruleFrom = '';
    this.ruleTo = '';
    if (this.cleanMode) this.openBook(this.current, true);
  }

  private clearRules(): void {
    if (!this.current || !this.store) return;
    this.store.saveRules(this.current, '');
    if (this.cleanMode) this.openBook(this.current, false);
  }

  private buildClean(book: BookMeta): string {
    if (!this.store) throw new Error('book store unavailable');
    let output = this.store.cleanPath(book);
    let base = new ReaderController();
    try {
      base.open(this.store.normalizedPath(book), 0);
      base.exportRules(this.store.rules(book), output);
    } finally {
      base.close();
    }
    return output;
  }

  private toggleClean(): void {
    if (this.current) this.openBook(this.current, !this.cleanMode);
  }

  private async exportClean(): Promise<void> {
    if (!this.current || !this.store) return;
    try {
      let cleanPath = this.buildClean(this.current);
      let context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      let options = new picker.DocumentSaveOptions();
      options.newFileNames = [`${this.stripTxt(this.current.name)}-净读.txt`];
      options.fileSuffixChoices = ['txt'];
      let documentPicker = new picker.DocumentViewPicker(context);
      let uris = await documentPicker.save(options);
      if (uris.length > 0) {
        this.store.copyToUri(cleanPath, uris[0]);
        this.toast('导出完成');
      }
    } catch (error) {
      this.showError(error, '导出失败');
    }
  }

  private stripTxt(name: string): string {
    return name.toLowerCase().endsWith('.txt') ? name.substring(0, name.length - 4) : name;
  }

  private async toggleTts(): Promise<void> {
    if (!this.current) return;
    this.stopAuto();
    if (this.ttsReading) {
      this.tts.stop('');
      this.ttsReading = false;
      return;
    }
    try {
      this.ttsReading = true;
      await this.tts.start(this.reader.handleValue(), this.reader.position(),
        (offset: number): void => { this.reader.jump(offset); this.render(); },
        (reason: string): void => {
          this.ttsReading = false;
          if (reason !== 'end' && reason.length > 0) this.toast(reason);
        });
    } catch (error) {
      this.ttsReading = false;
      this.showError(error, '朗读失败');
    }
  }

  private toggleAuto(): void {
    if (!this.current) return;
    if (this.autoReading) {
      this.stopAuto();
      return;
    }
    this.tts.stop('');
    this.ttsReading = false;
    this.autoReading = true;
    this.autoTimer = setInterval(() => {
      if (!this.current || this.reader.position() >= Math.max(0, this.reader.length() - 1)) {
        this.stopAuto();
        return;
      }
      this.reader.next();
      this.render();
    }, this.autoDelay);
  }

  private stopAuto(): void {
    this.autoReading = false;
    if (this.autoTimer >= 0) {
      clearInterval(this.autoTimer);
      this.autoTimer = -1;
    }
  }

  private setSleepTimer(minutes: number): void {
    this.cancelSleepTimer();
    if (minutes > 0) {
      this.sleepTimer = setTimeout(() => {
        this.stopAuto();
        this.tts.stop('sleep timer');
        this.ttsReading = false;
        this.toast('睡眠定时结束');
      }, minutes * 60 * 1000);
      this.toast(`已设置 ${minutes} 分钟`);
    } else {
      this.toast('已关闭睡眠定时');
    }
    this.panel = '';
  }

  private cancelSleepTimer(): void {
    if (this.sleepTimer >= 0) {
      clearTimeout(this.sleepTimer);
      this.sleepTimer = -1;
    }
  }

  private confirmDelete(): void {
    if (!this.current || !this.store) return;
    let book = this.current;
    this.getUIContext().showAlertDialog({
      title: '删除私有副本？',
      message: `${book.name}\n源 TXT 不会被删除。`,
      primaryButton: {
        value: '取消',
        action: () => {}
      },
      secondaryButton: {
        value: '删除',
        fontColor: Color.Red,
        action: () => {
          this.stopAuto();
          this.tts.stop('');
          this.ttsReading = false;
          this.reader.close();
          this.store?.delete(book);
          this.current = undefined;
          this.cleanMode = false;
          this.pageText = '导入本地 TXT 开始阅读。';
          this.status = '离线 · 无账号 · 无网络权限';
          this.panel = '';
          this.refreshLibrary();
          if (this.books.length > 0) this.openBook(this.books[0], false);
        }
      }
    });
  }

  private toast(message: string): void {
    this.getUIContext().getPromptAction().showToast({ message: message, duration: 1800 });
  }

  private showError(error: Object, prefix: string): void {
    let message = error instanceof Error ? error.message : JSON.stringify(error);
    this.toast(`${prefix}: ${message}`);
  }
}
