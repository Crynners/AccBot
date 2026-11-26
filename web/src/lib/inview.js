/**
 * Svelte action: calls `onEnter` once, the first time the element scrolls into view.
 * Runs immediately when IntersectionObserver is missing or the user prefers reduced motion.
 */
export function inview(node, onEnter) {
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  if (reduced || !('IntersectionObserver' in window)) {
    onEnter?.(true);
    return {};
  }
  const io = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        io.disconnect();
        onEnter?.(false);
      }
    },
    { threshold: 0.35 }
  );
  io.observe(node);
  return { destroy: () => io.disconnect() };
}
