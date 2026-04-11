let config = {
  mode: 'production',
  resolve: {
    modules: [
      "node_modules"
    ]
  },
  plugins: [],
  module: {
    rules: []
  }
};

// entry
config.entry = {
    main: [require('path').resolve(__dirname, "kotlin/retroarkanoid.mjs")]
};
config.output = {
    filename: (chunkData) => {
        return chunkData.chunk.name === 'main'
            ? "app.js"
            : "app-[name].js";
    },
    library: "app",
    libraryTarget: "umd",
    globalObject: "globalThis"
};
config.output.path = require('path').resolve(__dirname, "../../../../app/build/kotlin-webpack/wasmJs/productionExecutable")
// source maps
config.module.rules.push({
        test: /\.m?js$/,
        use: ["source-map-loader"],
        enforce: "pre"
});
config.devtool = 'source-map';
config.ignoreWarnings = [
    /Failed to parse source map/,
    /Accessing import\.meta directly is unsupported \(only property access or destructuring is supported\)/
]

// dev server
config.devServer = {
  "open": true,
  "static": [
    "/home/jarbas/Projetos/Games/Tenis_Web/app"
  ]
};

// noinspection JSUnnecessarySemicolon
;(function(config) {
    const tcErrorPlugin = require('kotlin-test-js-runner/tc-log-error-webpack');
    config.plugins.push(new tcErrorPlugin())
    config.stats = config.stats || {}
    Object.assign(config.stats, config.stats, {
        warnings: false,
        errors: false
    })
})(config);
config.experiments = {
    asyncWebAssembly: true,
    topLevelAwait: true,
}
module.exports = config
