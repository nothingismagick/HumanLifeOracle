package net.corda.examples.oracle.base.contract

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

const val HUMAN_LIFE_PROGRAM_ID: ContractClassName = "net.corda.examples.oracle.base.contract.HumanLifeContract"

class HumanLifeContract : Contract {
    // Commands signed by oracles must contain the facts the oracle is attesting to.
    class Create(val ssn: String, val alive: Boolean) : CommandData

    // Our contract does not check that the Human Life state is correct. Instead, it checks that the
    // information in the command and state match.
    override fun verify(tx: LedgerTransaction) = requireThat {
        "There are no inputs" using (tx.inputs.isEmpty())
        val output = tx.outputsOfType<HumanLifeState>().single()
        val command = tx.commands.requireSingleCommand<Create>().value
        "The SSN in the output does not match the ALIVE response in the command." using
                (command.ssn == output.ssn && command.alive == output.alive)
    }
}

// If SSN is valid, and discoverable, then alive will indicate if this person is living today
// `Requester` is the Party that will store this fact in its vault.
data class HumanLifeState(val ssn: String,
                      val alive: Boolean,
                      val requester: AbstractParty) : ContractState {
    override val participants: List<AbstractParty> get() = listOf(requester)
    override fun toString() = "The SSN ${ssn} belongs to a person that is ${if (alive) "LIVING" else "DECEASED"}."
}