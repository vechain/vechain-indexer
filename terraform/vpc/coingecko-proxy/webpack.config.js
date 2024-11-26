const path = require('path');

module.exports = {
  entry: './src/lambda.ts', // Entry point of your application
  module: {
    rules: [
      {
        test: /\.ts$/,
        use: 'ts-loader',
        exclude: /node_modules/,
      },
    ],
  },
  resolve: {
    extensions: ['.ts', '.js'],
  },
  output: {
    filename: 'lambda.js', // Output file name
    path: path.resolve(__dirname, 'dist'), // Output directory
    libraryTarget: 'commonjs2',
  },
  target: 'node', // Target environment
};