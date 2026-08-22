import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { preferences } from '@kit.ArkData';
import { util } from '@kit.ArkTS';
import nativeCore from 'libentry.so';

export interface BookMeta {
  id: string;
  name: string;
  encoding: string;
  progress: number;
  touchedAt: number;
}

export class BookStore {
  static readonly ENCODINGS: string[] = ['AUTO', 'UTF-8', 'GB18030', 'GBK', 'GB2312', 'Big5', 'UTF-16LE', 'UTF-16BE'];
  private readonly prefs: preferences.Preferences;
  private readonly root: string;

  constructor(context: common.UIAbilityContext) {
    this.root = `${context.filesDir}/books`;
    try { fs.mkdirSync(this.root); } catch (_) {}
    this.prefs = preferences.getPreferencesSync(context, { name: 'jingdu_library_v2' });
  }

  list(): BookMeta[] {
    let raw = this.prefs.getSync('books', '[]') as string;
    let parsed = JSON.parse(raw) as BookMeta[];
    let books: BookMeta[] = [];
    parsed.forEach((item: BookMeta): void => {
      try {
        fs.statSync(this.normalizedPath(item));
        books.push(item);
      } catch (_) {}
    });
    books.sort((a: BookMeta, b: BookMeta): number => b.touchedAt - a.touchedAt);
    return books;
  }

  importUri(uri: string, requestedEncoding: string): BookMeta {
    let id = `${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
    let dir = `${this.root}/${id}`;
    fs.mkdirSync(dir);
    let rawPath = `${dir}/source.bin`;
    let normalizedPath = `${dir}/document.txt`;
    let source = fs.openSync(uri, fs.OpenMode.READ_ONLY);
    let raw = fs.openSync(rawPath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
    let normalized = fs.openSync(normalizedPath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
    try {
      let sampleBuffer = new ArrayBuffer(64 * 1024);
      let sampleSize = fs.readSync(source.fd, sampleBuffer, { position: 0, offset: 0, length: sampleBuffer.byteLength });
      let sample = new Uint8Array(sampleBuffer, 0, sampleSize);
      let encoding = requestedEncoding === 'AUTO' ? nativeCore.detectEncoding(sample) : requestedEncoding;
      let decoder = util.TextDecoder.create(encoding.toLowerCase(), { ignoreBOM: false, fatal: false });
      let buffer = new ArrayBuffer(64 * 1024);
      let position = 0;
      while (true) {
        let count = fs.readSync(source.fd, buffer, { position: position, offset: 0, length: buffer.byteLength });
        if (count <= 0) break;
        fs.writeSync(raw.fd, buffer, { offset: 0, length: count });
        let bytes = new Uint8Array(buffer, 0, count);
        let chunk = decoder.decodeToString(bytes, { stream: true });
        if (chunk.length > 0) fs.writeSync(normalized.fd, chunk, { encoding: 'utf-8' });
        position += count;
      }
      let tail = decoder.decodeToString(new Uint8Array(new ArrayBuffer(0)), { stream: false });
      if (tail.length > 0) fs.writeSync(normalized.fd, tail, { encoding: 'utf-8' });
      let book: BookMeta = {
        id: id,
        name: this.nameFromUri(uri),
        encoding: encoding,
        progress: 0,
        touchedAt: Date.now()
      };
      let books = this.list();
      books.push(book);
      this.write(books);
      return book;
    } finally {
      fs.closeSync(source);
      fs.closeSync(raw);
      fs.closeSync(normalized);
    }
  }

  normalizedPath(book: BookMeta): string { return `${this.root}/${book.id}/document.txt`; }
  cleanPath(book: BookMeta): string { return `${this.root}/${book.id}/clean.txt`; }

  saveProgress(book: BookMeta, progress: number): void {
    book.progress = Math.max(0, progress);
    book.touchedAt = Date.now();
    let books = this.list().filter((item: BookMeta): boolean => item.id !== book.id);
    books.push(book);
    this.write(books);
  }

  delete(book: BookMeta): void {
    for (let path of [`${this.root}/${book.id}/source.bin`, this.normalizedPath(book), this.cleanPath(book)]) {
      try { fs.unlinkSync(path); } catch (_) {}
    }
    try { fs.rmdirSync(`${this.root}/${book.id}`); } catch (_) {}
    this.write(this.list().filter((item: BookMeta): boolean => item.id !== book.id));
    this.prefs.deleteSync(`rules.${book.id}`);
    this.prefs.deleteSync(`bookmarks.${book.id}`);
    this.prefs.flush();
  }

  copyToUri(sourcePath: string, uri: string): void {
    let source = fs.openSync(sourcePath, fs.OpenMode.READ_ONLY);
    let target = fs.openSync(uri, fs.OpenMode.READ_WRITE | fs.OpenMode.TRUNC);
    try {
      let buffer = new ArrayBuffer(64 * 1024);
      let position = 0;
      while (true) {
        let count = fs.readSync(source.fd, buffer, { position: position, offset: 0, length: buffer.byteLength });
        if (count <= 0) break;
        fs.writeSync(target.fd, buffer, { offset: 0, length: count });
        position += count;
      }
    } finally {
      fs.closeSync(source);
      fs.closeSync(target);
    }
  }

  rules(book: BookMeta): string { return this.prefs.getSync(`rules.${book.id}`, '') as string; }
  saveRules(book: BookMeta, rules: string): void {
    this.prefs.putSync(`rules.${book.id}`, rules);
    this.prefs.flush();
  }

  bookmarks(book: BookMeta): number[] {
    let raw = this.prefs.getSync(`bookmarks.${book.id}`, '[]') as string;
    return JSON.parse(raw) as number[];
  }

  saveBookmarks(book: BookMeta, values: number[]): void {
    this.prefs.putSync(`bookmarks.${book.id}`, JSON.stringify(values));
    this.prefs.flush();
  }

  private write(items: BookMeta[]): void {
    this.prefs.putSync('books', JSON.stringify(items));
    this.prefs.flush();
  }

  private nameFromUri(uri: string): string {
    let index = uri.lastIndexOf('/');
    let name = index >= 0 ? uri.substring(index + 1) : 'TXT';
    try { return decodeURIComponent(name); } catch (_) { return name; }
  }
}
