import Foundation

/// Runs an async operation with a timeout. Returns `nil` if the timeout elapses first.
func withTimeoutOrNil<T>(seconds: TimeInterval, operation: @escaping () async -> T?) async -> T? {
    await withTaskGroup(of: T?.self) { group in
        group.addTask { await operation() }
        group.addTask {
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            return nil
        }
        // Return the first result; if timeout wins, cancel the other
        let result = await group.next() ?? nil
        group.cancelAll()
        return result
    }
}
