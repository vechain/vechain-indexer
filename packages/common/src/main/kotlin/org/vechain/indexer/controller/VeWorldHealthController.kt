package org.vechain.indexer.controller

import org.springframework.boot.actuate.health.HealthComponent
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.Status
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class VeWorldHealthController(
    private val healthEndpoint: HealthEndpoint
) {
    
    @GetMapping("/veworld/health")
    fun health(): ResponseEntity<HealthComponent> {
        val health = healthEndpoint.health()

        return if (health.status == Status.UP) {
            ResponseEntity.ok(health)
        } else {
            ResponseEntity.status(503).body(health)
        }
    }

}