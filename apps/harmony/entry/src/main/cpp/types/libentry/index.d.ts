export interface JingduNativeCore {
  abiVersion(): number;
  detectEncoding(sample: Uint8Array): string;
  open(path: string): number;
  close(handle: number): void;
  charCount(handle: number): number;
  read(handle: number, offset: number, count: number): string;
  search(handle: number, query: string, limit: number): string;
  chapters(handle: number, limit: number): string;
  speechChunk(handle: number, offset: number, count: number): string;
  exportRules(handle: number, packedRules: string, outputPath: string): boolean;
}

declare const nativeCore: JingduNativeCore;
export default nativeCore;
