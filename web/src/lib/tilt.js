/**
 * Svelte action: tilts the phone inside `node` toward the pointer (mouse only, max 7 degrees).
 * Does nothing on touch devices or when the user prefers reduced motion.
 */
export function tilt(node) {
  const fine = window.matchMedia?.('(pointer: fine)').matches;
  const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const phone = node.querySelector('.phone-frame');
  if (!fine || reduced || !phone) return {};

  const area = node.closest('section') ?? node;
  let frame = 0;

  function move(e) {
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      const r = phone.getBoundingClientRect();
      const dx = (e.clientX - (r.left + r.width / 2)) / window.innerWidth;
      const dy = (e.clientY - (r.top + r.height / 2)) / window.innerHeight;
      phone.style.transform = `perspective(1200px) rotateY(${(dx * 14).toFixed(2)}deg) rotateX(${(-dy * 10).toFixed(2)}deg)`;
    });
  }

  function leave() {
    cancelAnimationFrame(frame);
    phone.style.transform = '';
  }

  phone.style.transition = 'transform 0.25s ease-out';
  area.addEventListener('pointermove', move);
  area.addEventListener('pointerleave', leave);
  return {
    destroy() {
      area.removeEventListener('pointermove', move);
      area.removeEventListener('pointerleave', leave);
    }
  };
}
