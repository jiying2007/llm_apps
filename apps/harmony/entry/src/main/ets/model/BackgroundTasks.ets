import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import nativeCore from 'libentry.so';

export interface ImportTaskResult {
  id: string;
  encoding: string;
  size: number;
  sourceSha256: string;
  normalizedSha256: string;
}

export interface OpenTaskResult {
  handle: number;
  length: number;
  path: string;
}

function copyFile(sourcePath: string, targetPath: string): number {
  let source = fs.openSync(sourcePath, fs.OpenMode.READ_ONLY);
  let target = fs.openSync(targetPath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
  let total = 0;
  try {
    let buffer = new ArrayBuffer(64 * 1024);
    while (true) {
      let count = fs.readSync(source.fd, buffer, { offset: 0, length: buffer.byteLength });
      if (count <= 0) break;
      fs.writeSync(target.fd, buffer, { offset: 0, length: count });
      total += count;
    }
    fs.fsyncSync(target.fd);
  } finally {
    fs.closeSync(source);
    fs.closeSync(target);
  }
  return total;
}

function readSmallText(path: string): string {
  try {
    let file = fs.openSync(path, fs.OpenMode.READ_ONLY);
    try {
      let size = Math.min(128, fs.statSync(path).size);
      if (size <= 0) return '';
      let buffer = new ArrayBuffer(size);
      let count = fs.readSync(file.fd, buffer, { offset: 0, length: size });
      if (count <= 0) return '';
      return util.TextDecoder.create('utf-8').decodeToString(new Uint8Array(buffer, 0, count));
    } finally {
      fs.closeSync(file);
    }
  } catch (_) {
    return '';
  }
}

function writeTextFile(path: string, value: string): void {
  let file = fs.openSync(path, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
  try {
    fs.writeSync(file.fd, value, { encoding: 'utf-8' });
    fs.fsyncSync(file.fd);
  } finally {
    fs.closeSync(file);
  }
}

function fileExists(path: string): boolean {
  try {
    fs.statSync(path);
    return true;
  } catch (_) {
    return false;
  }
}

function normalize(rawPath: string, normalizedPath: string, requestedEncoding: string): string {
  let raw = fs.openSync(rawPath, fs.OpenMode.READ_ONLY);
  let sampleBuffer = new ArrayBuffer(64 * 1024);
  let sampleSize = 0;
  try {
    sampleSize = fs.readSync(raw.fd, sampleBuffer, { position: 0, offset: 0, length: sampleBuffer.byteLength });
  } finally {
    fs.closeSync(raw);
  }
  let sample = new Uint8Array(sampleBuffer, 0, sampleSize);
  let totalSize = fs.statSync(rawPath).size;
  let encoding = requestedEncoding === 'AUTO'
    ? nativeCore.detectEncoding(sample, totalSize > sampleSize)
    : requestedEncoding;

  let decoder = util.TextDecoder.create(encoding.toLowerCase(), { ignoreBOM: false, fatal: false });
  let source = fs.openSync(rawPath, fs.OpenMode.READ_ONLY);
  let output = fs.openSync(normalizedPath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
  try {
    let buffer = new ArrayBuffer(64 * 1024);
    while (true) {
      let count = fs.readSync(source.fd, buffer, { offset: 0, length: buffer.byteLength });
      if (count <= 0) break;
      let chunk = decoder.decodeToString(new Uint8Array(buffer, 0, count), { stream: true });
      if (chunk.length > 0) fs.writeSync(output.fd, chunk, { encoding: 'utf-8' });
    }
    let tail = decoder.decodeToString(new Uint8Array(new ArrayBuffer(0)), { stream: false });
    if (tail.length > 0) fs.writeSync(output.fd, tail, { encoding: 'utf-8' });
    fs.fsyncSync(output.fd);
  } finally {
    fs.closeSync(source);
    fs.closeSync(output);
  }
  return encoding;
}

@Concurrent
export function importBookTask(sourceUri: string, booksRoot: string, requestedEncoding: string): string {
  let nonce = `${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
  let tempPath = `${booksRoot}/.import-${nonce}.bin`;
  let size = copyFile(sourceUri, tempPath);
  let sourceSha256 = nativeCore.fileSha256(tempPath);
  let dir = `${booksRoot}/${sourceSha256}`;
  try { fs.mkdirSync(dir); } catch (_) {}
  let rawPath = `${dir}/source.bin`;
  try { fs.unlinkSync(rawPath); } catch (_) {}
  copyFile(tempPath, rawPath);
  try { fs.unlinkSync(tempPath); } catch (_) {}
  let normalizedPath = `${dir}/document.txt`;
  let encoding = normalize(rawPath, normalizedPath, requestedEncoding);
  let normalizedSha256 = nativeCore.fileSha256(normalizedPath);
  let result: ImportTaskResult = {
    id: sourceSha256,
    encoding: encoding,
    size: size,
    sourceSha256: sourceSha256,
    normalizedSha256: normalizedSha256
  };
  return JSON.stringify(result);
}

@Concurrent
export function openDocumentTask(path: string): string {
  if (nativeCore.abiVersion() !== 2) throw new Error('unsupported Jingdu core ABI');
  let handle = nativeCore.open(path);
  let result: OpenTaskResult = {
    handle: handle,
    length: nativeCore.charCount(handle),
    path: path
  };
  return JSON.stringify(result);
}

@Concurrent
export function searchTask(path: string, query: string, limit: number): string {
  let handle = nativeCore.open(path);
  try { return nativeCore.search(handle, query, limit); }
  finally { nativeCore.close(handle); }
}

@Concurrent
export function chaptersTask(path: string, limit: number): string {
  let handle = nativeCore.open(path);
  try { return nativeCore.chapters(handle, limit); }
  finally { nativeCore.close(handle); }
}

@Concurrent
export function cleanTask(normalizedPath: string, normalizedSha256: string,
  rules: string, outputPath: string): string {
  let revision = nativeCore.repairRevision(normalizedSha256, rules);
  let revisionPath = outputPath.endsWith('.txt')
    ? `${outputPath.substring(0, outputPath.length - 4)}.revision`
    : `${outputPath}.revision`;
  if (fileExists(outputPath) && readSmallText(revisionPath) === revision) return revision;

  let handle = nativeCore.open(normalizedPath);
  try { nativeCore.exportRules(handle, rules, outputPath); }
  finally { nativeCore.close(handle); }
  writeTextFile(revisionPath, revision);
  return revision;
}

@Concurrent
export function exportTask(sourcePath: string, destinationUri: string): number {
  return copyFile(sourcePath, destinationUri);
}

@Concurrent
export function writeTextTask(path: string, value: string): void {
  writeTextFile(path, value);
}
