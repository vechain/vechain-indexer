package org.vechain.indexer.controller

import com.fasterxml.jackson.annotation.JsonView
import org.jetbrains.annotations.TestOnly
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.E2E_PATH
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.service.E2EService
import org.vechain.thor.model.Views

@Profile("e2e")
@RestController
@RequestMapping(E2E_PATH)
open class E2EController(private val e2EService: E2EService) {

    @GetMapping("/nfts-archives")
    @JsonView(Views.Internal::class)
    @TestOnly
    open fun getNFTArchives(): List<Archive<IndexedNFT>> {
        return e2EService.getNftArchives()
    }

    @GetMapping("/transfers")
    @TestOnly
    open fun getNFTTransfers(): List<IndexedTransferEvent> {
        return e2EService.getNftTransfers()
    }

    @GetMapping("/nfts")
    @JsonView(Views.Internal::class)
    @TestOnly
    open fun getNFTs(): List<IndexedNFT> {
        return e2EService.getNfts()
    }
}
