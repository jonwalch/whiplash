const path = require("path");
const webpack = require("webpack");

module.exports = {
  entry: "./resources/public/js/index.tsx",
  mode: "development",
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
  devtool: "source-map",
  resolve: { extensions: ["*", ".js", ".jsx", ".ts", ".tsx"] },
  output: {
    path: path.resolve(__dirname, "resources/public/dist/"),
    publicPath: "/dist/",
    filename: "bundle.js"
  },
  plugins: [new webpack.HotModuleReplacementPlugin()]
  //      externals: {
  //          "react": "React",
  //          "react-dom": "ReactDOM"
  //      }
};
