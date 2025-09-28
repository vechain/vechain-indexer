package org.vechain.monitor.vtho

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Profile("vtho-funds")
@EnableScheduling
@Component
open class VthoFundsMonitor {
    @Scheduled(
        initialDelayString = "\${monitor.vthoFunds.initialDelay}",
        fixedRateString = "\${monitor.vthoFunds.interval}",
    )
    override fun run() {
        // First, get the balance for each account
        val balances = vthoService.getVTHOBalances(items.keys.toList())

        val lowBalances: MutableMap<String, BigDecimal> = HashMap()

        balances.forEach { (address, balance) ->
            val tracker = vthoMessageTrackerRepo.getAccount(address)
            val vthoThreshold =
                items[address]?.vthoThreshold
                    ?: throw IllegalArgumentException(
                        "VTHO threshold not defined for address: $address",
                    )

            if (balance < BigDecimal(vthoThreshold) && (tracker?.notified != true)) {
                // Set the notification status to true if the balance is below the threshold
                vthoMessageTrackerRepo.updateAccount(address, true)
                lowBalances.put(address, balance)
            } else if (balance >= BigDecimal(vthoThreshold) && tracker?.notified == true) {
                // Reset the notification status if the balance is above the threshold
                vthoMessageTrackerRepo.updateAccount(address, false)
            }
        }

        if (lowBalances.isNotEmpty()) {
            sendLowVTHOMessage(lowBalances)
        } else {
            logger.info("No low VTHO balances to notify about")
        }
    }

    fun sendLowVTHOMessage(lowBalances: Map<String, BigDecimal>) {
        val message = messageService.buildGroupedLowVTHOMessage(lowBalances)
        slackService.sendMessage(message, env.vthoFunds.slackUrl)
    }
}
