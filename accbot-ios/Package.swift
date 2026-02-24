// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AccBot",
    platforms: [.iOS(.v16)],
    dependencies: [
        .package(url: "https://github.com/groue/GRDB.swift.git", from: "6.24.0"),
    ],
    targets: [
        .target(
            name: "AccBot",
            dependencies: [
                .product(name: "GRDB", package: "GRDB.swift"),
            ],
            exclude: ["AccBotApp.swift", "AppDelegate.swift"]
        ),
        .testTarget(
            name: "AccBotTests",
            dependencies: ["AccBot"]
        ),
    ]
)
