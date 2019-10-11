const path = require("path");
const webpack = require("webpack");

module.exports = {
      entry: "./resources/public/js/index.js",
      mode: "development",
      module: {
              rules: [
                        {
                                    test: /\.(js|jsx)$/,
                                    exclude: /(node_modules|bower_components)/,
                                    loader: "babel-loader",
                                    options: { presets: ["@babel/env"] }
                                  },
                        {
                                    test: /\.css$/,
                                    use: ["style-loader", "css-loader"]
                                  }
                      ]
            },
      resolve: { extensions: ["*", ".js", ".jsx"] },
      output: {
              path: path.resolve(__dirname, "resources/public/dist/"),
              publicPath: "/public/dist/",
              filename: "bundle.js"
            },
      devServer: {
              contentBase: path.join(__dirname, "resources/html/"),
              port: 3000,
              publicPath: "http://localhost:3000/public/dist/",
              hotOnly: true
            },
      plugins: [new webpack.HotModuleReplacementPlugin()]
};
