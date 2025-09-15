package contracts.resource

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description("Download MP3 file by ID")
    request {
        method 'GET'
        url '/resources/1'
    }
    response {
        status 200
        headers {
            contentType('audio/mpeg')
            header('Content-Disposition', regex('attachment;.*filename=.*'))
        }
        body(fileAsBytes("test.mp3"))
    }
}