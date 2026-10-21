// Staff display helpers, pure.

/** Up to two letters for an avatar: first + last word, ignoring a parenthesised suffix. */
export function initials(name: string): string {
  const parts = name
    .replace(/\(.*?\)/g, '')
    .trim()
    .split(/\s+/)
    .filter((p) => p !== '');
  const first = parts[0] ?? '';
  const last = parts[parts.length - 1] ?? '';
  const letters = parts.length > 1 ? `${first.charAt(0)}${last.charAt(0)}` : first.slice(0, 2);
  return letters === '' ? '?' : letters.toUpperCase();
}
