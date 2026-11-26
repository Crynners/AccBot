import adapter from '@sveltejs/adapter-static';

/** @type {import('@sveltejs/kit').Config} */
const config = {
  kit: {
    adapter: adapter({ strict: true }),
    prerender: {
      handleHttpError: 'fail'
    }
  }
};

export default config;
