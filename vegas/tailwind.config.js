import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        graphite: {
          950: "#0C0C0C",
          900: "#161616",
          800: "#1F1F1F",
          700: "#2A2A2A",
        },
        emerald: {
          400: "#34D399",
          500: "#10B981",
          600: "#059669",
        },
      },
    },
  },
  plugins: [],
};
export default config;