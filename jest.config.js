module.exports = {
    "transform": {
        ".(ts|tsx)": "<rootDir>/node_modules/ts-jest/preprocessor.js"
    },
    "testRegex": "(/__tests__/.*|\\.(test|spec))\\.(ts|tsx|js)$",
    "moduleFileExtensions": [
        "ts",
        "tsx",
        "js"
    ],
    "moduleNameMapper": {
        '^axios$': require.resolve('axios'),
    },
    "testPathIgnorePatterns": [
        "/node_modules/",
        "<rootDir>/common/build/",
        "<rootDir>/formulaire/build/",
        "<rootDir>/formulaire/out/",
        "<rootDir>/formulaire-public/build/",
        "<rootDir>/formulaire-public/out/"
    ],
    "verbose": true,
    "testURL": "http://localhost/",
    "coverageDirectory": "coverage/front",
    "coverageReporters": [
        "text",
        "cobertura"
    ],
    "moduleNameMapper": {
        "^@common(.*)$": "<rootDir>/common/src/main/resources/ts$1",
        "^@formulaire(.*)$": "<rootDir>/formulaire/src/main/resources/public/ts$1",
        "^@formulairepublic(.*)$": "<rootDir>/formulaire-public/src/main/resources/public/ts$1"
    }
};
