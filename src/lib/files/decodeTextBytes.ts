export function decodeTextBytes(bytes: Uint8Array): string {
  const decode = (encoding: string, offset = 0) =>
    new TextDecoder(encoding, { fatal: true }).decode(bytes.subarray(offset));
  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) return decode("utf-8", 3);
  if (bytes[0] === 0xff && bytes[1] === 0xfe) return decode("utf-16le", 2);
  if (bytes[0] === 0xfe && bytes[1] === 0xff) return decode("utf-16be", 2);
  try {
    return decode("utf-8");
  } catch {
    return new TextDecoder("gb18030").decode(bytes);
  }
}
