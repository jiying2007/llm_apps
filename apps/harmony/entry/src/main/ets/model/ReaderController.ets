import nativeCore from 'libentry.so';

export interface SearchHit {
  offset: number;
  context: string;
}

export interface ChapterItem {
  offset: number;
  title: string;
}

export interface SpeechChunk {
  nextOffset: number;
  text: string;
}

export class ReaderController {
  static readonly PAGE_CHARS: number = 1800;
  static readonly WINDOW_CHARS: number = 6000;
  private handle: number = 0;
  private lengthValue: number = 0;
  private positionValue: number = 0;

  open(path: string, restoredPosition: number): void {
    this.close();
    this.handle = nativeCore.open(path);
    this.lengthValue = nativeCore.charCount(this.handle);
    this.jump(restoredPosition);
  }

  close(): void {
    if (this.handle !== 0) nativeCore.close(this.handle);
    this.handle = 0;
    this.lengthValue = 0;
    this.positionValue = 0;
  }

  handleValue(): number { return this.handle; }
  position(): number { return this.positionValue; }
  length(): number { return this.lengthValue; }

  page(): string {
    this.ensureOpen();
    return nativeCore.read(this.handle, this.positionValue, ReaderController.WINDOW_CHARS);
  }

  jump(value: number): void {
    let max = Math.max(0, this.lengthValue - 1);
    this.positionValue = Math.min(Math.max(0, value), max);
  }

  next(): void { this.jump(this.positionValue + ReaderController.PAGE_CHARS); }
  previous(): void { this.jump(this.positionValue - ReaderController.PAGE_CHARS); }

  search(query: string): SearchHit[] {
    this.ensureOpen();
    let result: SearchHit[] = [];
    nativeCore.search(this.handle, query, 500).split('\n').forEach((line: string): void => {
      let tab = line.indexOf('\t');
      if (tab <= 0) return;
      let offset = Number(line.substring(0, tab));
      if (Number.isFinite(offset)) result.push({ offset: offset, context: line.substring(tab + 1) });
    });
    return result;
  }

  chapters(): ChapterItem[] {
    this.ensureOpen();
    let result: ChapterItem[] = [];
    nativeCore.chapters(this.handle, 20000).split('\n').forEach((line: string): void => {
      let tab = line.indexOf('\t');
      if (tab <= 0) return;
      let offset = Number(line.substring(0, tab));
      if (Number.isFinite(offset)) result.push({ offset: offset, title: line.substring(tab + 1) });
    });
    return result;
  }

  speech(from: number): SpeechChunk {
    this.ensureOpen();
    let packed = nativeCore.speechChunk(this.handle, Math.max(0, from), 900);
    let tab = packed.indexOf('\t');
    if (tab <= 0) return { nextOffset: from, text: '' };
    let next = Number(packed.substring(0, tab));
    if (!Number.isFinite(next)) return { nextOffset: from, text: '' };
    return { nextOffset: next, text: packed.substring(tab + 1) };
  }

  exportRules(packedRules: string, outputPath: string): void {
    this.ensureOpen();
    nativeCore.exportRules(this.handle, packedRules, outputPath);
  }

  private ensureOpen(): void {
    if (this.handle === 0) throw new Error('no document open');
  }
}
