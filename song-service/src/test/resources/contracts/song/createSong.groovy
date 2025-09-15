package contracts.song

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "should create a song"
    request {
        method POST()
        url "/songs"
        body([
                id: 1,
                name: "Unknown Title",
                artist: "Test Artist",
                album: "Test Album",
                duration: "00:07",
                year: "2025"
        ])
        headers {
            contentType(applicationJson())
        }
    }
    response {
        status 200
        body([
                id: 1
        ])
        headers {
            contentType(applicationJson())
        }
    }
}