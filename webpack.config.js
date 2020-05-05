const path = require("path");
const webpack = require("webpack");
const HtmlWebpackPlugin = require('html-webpack-plugin');

let config = {
  entry: {
    app: "./src/js/index.tsx",
    twitchExt: "./src/js/twitch-ext-index.tsx",
  },
  module: {
    rules: [
      {
        test: /\.ts(x?)$/,
        exclude: /(node_modules|bower_components)/,
        loader: "ts-loader"
      },
      {
        enforce: "pre",
        test: /\.js$/,
        loader: "source-map-loader"
      },
      {
        test: /\.css$/,
        use: ["style-loader", "css-loader"]
      },
      {
        test: /\.(woff(2)?|ttf|eot|svg)(\?v=\d+\.\d+\.\d+)?$/,
        use: [
          {
            loader: 'file-loader',
            options: {
              name: '[name].[ext]',
              outputPath: 'resources/public/fonts/'
            }
          }
        ]
      }
    ]
  },
  // actually encouraged in all envs by webpack https://webpack.js.org/guides/production/#source-mapping
  devtool: 'source-map',
  resolve: { extensions: ["*", ".js", ".jsx", ".ts", ".tsx"] },
  output: {
    path: path.resolve(__dirname, "resources/public/dist/"),
    publicPath: "/dist/",
    filename: "[name].[contenthash].js"
  },
  plugins: [
    // ignore moment.js locales, see https://github.com/jmblog/how-to-optimize-momentjs-with-webpack#using-ignoreplugin
    new webpack.IgnorePlugin(/^\.\/locale$/, /moment$/),
    new HtmlWebpackPlugin({
      template: "resources/html/index.html",
      chunks: ["app"]
    }),
    new HtmlWebpackPlugin({
      filename: "error.html",
      template: "resources/html/error.html",
      inject: false,
    }),
    new HtmlWebpackPlugin({
      filename: "twitch-extension.html",
      template: "resources/html/twitch-extension.html",
      chunks: ["twitchExt"]
    })
  ]
  //      externals: {
  //          "react": "React",
  //          "react-dom": "ReactDOM"
  //      }
};

module.exports = (env, argv) => {
  if (argv.mode === 'development') {
    config.mode = 'development';
    config.output.filename = "bundle.js";
    config.plugins.push(new webpack.HotModuleReplacementPlugin())
  }

  else {
    config.mode = 'production'
  }

  return config;
};
