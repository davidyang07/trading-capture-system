module.exports = {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
    "postcss-pxtorem": {
      rootValue: 16,
      propList: ["*"],
      unitPrecision: 3,
      minPixelValue: 8,
      exclude: ["node_modules"],
    },
  },
};
