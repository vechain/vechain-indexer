package org.vechain.indexer.nft.e2e

import com.fasterxml.jackson.annotation.JsonView
import org.jetbrains.annotations.TestOnly
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.E2E_PATH
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.nft.NftArchive
import org.vechain.indexer.thor.model.Views
import org.vechain.indexer.transfer.IndexedTransferEvent

@Profile("e2e")
@RestController
@RequestMapping(E2E_PATH)
open class E2EController(private val e2EService: E2EService) {

    @GetMapping("/nft-archives")
    @JsonView(Views.Internal::class)
    @TestOnly
    open fun getNFTArchives(): List<NftArchive> {
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
    open fun getNFTs(): List<IndexedNft> {
        return e2EService.getNfts()
    }
}
