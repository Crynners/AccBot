import { error } from '@sveltejs/kit';
import { loadDict } from '$lib/locales.server.js';

export function load({ params }) {
  const dict = loadDict(params.lang);
  if (!dict) error(404, 'Not found');
  return { lang: params.lang, dict };
}
