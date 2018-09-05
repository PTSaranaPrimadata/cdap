const genDefaultConfig = require("@storybook/react/dist/server/config/defaults/webpack.config.js");
const cdapconfig = require('../webpack.config.cdap');
const path = require("path");

module.exports = (baseConfig, env) => {
  const config = genDefaultConfig(baseConfig, env);

  config.resolve.extensions.push(".ts", ".tsx", ".js");
  config.resolve.alias = cdapconfig.resolve.alias;

  config.module.rules[0].test = /\.(ts|tsx|js)$/;
  config.module.rules[0].query.presets = [
    "@babel/preset-env",
    "@babel/preset-react",
    "@babel/preset-typescript"
  ];

  config.module.rules.unshift({
    test: /\.(ts|tsx|js)$/,
    loader: require.resolve("ts-loader"),
    include: [path.resolve(__dirname, "../src")],
    options: {
      transpileOnly: true
    }
  }, {
    test: /\.scss$/,
    use: [
      'style-loader',
      'css-loader',
      'postcss-loader',
      'sass-loader'
    ]
  });

  // [ts-loader, babel-loader, ...]

  return config;
};