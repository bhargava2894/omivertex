import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import prettier from 'eslint-config-prettier';

export default [
  { ignores: ['dist', 'node_modules'] },
  js.configs.recommended,
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // count components referenced in JSX as "used" so no-unused-vars is accurate
      'react/jsx-uses-vars': 'error',
      'react/jsx-uses-react': 'error',
      'react-refresh/only-export-components': 'warn',
      // real mistakes are errors; allow intentionally-unused args prefixed with _
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      // advisory (surface, don't block): React-Compiler-era optimization rules that
      // flag intentional patterns (the generic useLoad(loader, deps) hook, a
      // render-time chart accumulator). Rules-of-hooks etc. remain errors.
      'react-hooks/exhaustive-deps': 'warn',
      'react-hooks/set-state-in-effect': 'warn',
      'react-hooks/immutability': 'warn',
      'react-hooks/use-memo': 'warn',
    },
  },
  prettier, // disable stylistic rules Prettier owns, so the two never fight
];
