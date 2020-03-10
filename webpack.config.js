const path = require("path");
const webpack = require("webpack");

let config = {
  entry: "./resources/public/js/index.tsx",
  module: {
    rules: [
      {
        test: /\.ts(x?)$/,
        exclude: /(node_modules|bower_components)/,
        loader: "ts-loader"
        // loader: "babel-loader",
        //options: { presets: ["@babel/env"] }
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
    filename: "bundle.js"
  },
  plugins: [
    // ignore moment.js locales, see https://github.com/jmblog/how-to-optimize-momentjs-with-webpack#using-ignoreplugin
    new webpack.IgnorePlugin(/^\.\/locale$/, /moment$/)
  ]
  //      externals: {
  //          "react": "React",
  //          "react-dom": "ReactDOM"
  //      }
};

module.exports = (env, argv) => {
  if (argv.mode === 'development') {
    config.mode = 'development';
    config.plugins.push(new webpack.HotModuleReplacementPlugin())
  }

  else {
    config.mode = 'production'
  }

  return config;
};
