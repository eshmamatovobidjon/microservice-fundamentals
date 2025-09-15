package contracts.resource

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("Upload an MP3 resource")
    request {
        method 'POST'
        url '/resources'
        headers {
            contentType('audio/mpeg')
        }
        body(fileAsBytes("test.mp3"))
    }
    response {
        status 200
        headers {
            contentType(applicationJson())
        }
        body([
                id: 1L
        ])
    }
}