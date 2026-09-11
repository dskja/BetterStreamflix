import SwiftSoup

extension Elements {
    func array() -> [Element] {
        Array(self)
    }
}

extension Element {
    func selectFirst(_ cssQuery: String) throws -> Element? {
        try select(cssQuery).first()
    }
}
