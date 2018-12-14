package net.corda.examples.oracle.service.service

import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.examples.oracle.base.contract.HumanLifeContract
import java.math.BigInteger
import java.io.DataOutputStream
import com.sun.net.ssl.HttpsURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.io.BufferedReader
import java.io.InputStreamReader

// We sub-class 'SingletonSerializeAsToken' to ensure that instances of this class are never serialised by Kryo.
// When a flow is check-pointed, the annotated @Suspendable methods and any object referenced from within those
// annotated methods are serialised onto the stack. Kryo, the reflection based serialisation framework we use, crawls
// the object graph and serialises anything it encounters, producing a graph of serialised objects.
// This can cause issues. For example, we do not want to serialise large objects on to the stack or objects which may
// reference databases or other external services (which cannot be serialised!). Therefore we mark certain objects with
// tokens. When Kryo encounters one of these tokens, it doesn't serialise the object. Instead, it creates a
// reference to the type of the object. When flows are de-serialised, the token is used to connect up the object
// reference to an instance which should already exist on the stack.
@CordaService
class Oracle(val services: ServiceHub) : SingletonSerializeAsToken() {
    private val myKey = services.myInfo.legalIdentities.first().owningKey

    // Generates a list of natural numbers and filters out the non-primes.
    // The reason why prime numbers were chosen is because they are easy to reason about and reduce the mental load
    // for this tutorial application.
    // Clearly, most developers can generate a list of primes and all but the largest prime numbers can be verified
    // deterministically in reasonable time. As such, it would be possible to add a constraint in the
    // [PrimeContract.verify] function that checks the nth prime is indeed the specified number.
    private val primes = generateSequence(1) { it + 1 }.filter { BigInteger.valueOf(it.toLong()).isProbablePrime(16) }

    // Returns the ALIVE reponse for a valid SSN
    fun query(ssn: String): Boolean {
        // Remove hyphens from the SSN, and make sure it is 9 characters long
        val unformattedSSN : String = "${ssn.replace("-", "")}"
        require(unformattedSSN.length == 9) { "SSN is not valid.  Must contain 9 numbers" }

        // Break the ssn into parts
        val ssnFirstPart : String = unformattedSSN.subSequence(0, 3) as String
        val ssnSecondPart : String = unformattedSSN.subSequence(3, 5) as String
        val ssnThirdPart : String = unformattedSSN.subSequence(5, 9) as String

        // Lookup the SSN
        val alive : Boolean = this.lookupSSN(ssnFirstPart, ssnSecondPart, ssnThirdPart)

        // Return our response
        return alive
    }


    fun lookupSSN(ssnFirstPart: String, ssnSecondPart : String, ssnThirdPart : String) : Boolean {
        val serverURL: String = "https://www.ssnvalidator.com/index.aspx"
        val url = URL(serverURL)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 300000
        connection.connectTimeout = 300000
        connection.doOutput = true

        // Create the POST message body
        val message : String = "ctl00%24ContentPlaceHolder1%24SsnFirstPartTextBox=" + ssnFirstPart + "&" +
            "ctl00%24ContentPlaceHolder1%24SsnSecondPartTextBox=" + ssnSecondPart + "&" +
            "ctl00%24ContentPlaceHolder1%24SsnThirdPartTextBox=" + ssnThirdPart + "&" +
            "ctl00%24ContentPlaceHolder1%24accept_terms=" + "YesRadioButton" + "&" +
            "ctl00%24ContentPlaceHolder1%24SubmitButton=" + "Search" + "&" +
            "__VIEWSTATE=" + "dZMeGrMbyE0yTfuPkc1Effhju7p0g4r9DEh0giDP91%2BZ4ktXR7t1Kt%2FA2hCdYX5An4qVRbimpq4kaSTdPXa47EDvg7uQKuOdGPca9HFrE4mNmPI3w7kUCVKkQYkkJ0Po49hbh%2Fp5URxo7nkJUWQlC2blMTUyJIWUzZutsIKURWwPWsOmCl6HwnKBYsJ3%2FxbI95dzgSG%2FF7Au54K9ccI%2FcB10%2FgdS%2BHjUZ4lR4yWpS3pN1hNyFOoZn%2Brc2iwQXhjqavSPsGnyhiXYPiZmsVEwA65zc%2BjbRoQgFXT9YWmQcACzgk2M%2FuIPZbiahqkjz0tKdDABf%2FtNdy8EdiL4GU1zdcXteFQahm8oV%2BH3Sueau5Hii33XwOuBM83KxfJLc4PmNet3Xx%2BoRKdLqmO9S46l5cd%2Bqtd80kXWXTevBl2aPxp8m9HcpvcOzRZm14u%2BCFJL8iyDR4DDNO5oXNChIuGi7vwxT3YdPFurHUyn2PeeKbujHYiVo6DyYbEGZ793rPJQ%0A%0A" + "&" +
            "__VIEWSTATEGENERATOR=" + "90059987" + "&" +
            "__EVENTVALIDATION=" + "4G1ywvIFIRG3SnJyks%2B6SRtQDDscEqwtR26fesR5%2Bh5O5T0UG4v7F8LAxBgqvqYtWcbyfg1aIs7mEO97FJKDokp0OB3OdQSoG7lx%2Beje3FpViL9M5eP8fXO%2FBzRfRQ04UWRi9mx9oFnLql0P5fg1QgygrYvQnH3%2F84I64PHpGRyqzF8l%2BKEKnFGAEy7K0QjoE5YAQQX%2FPuQTIzNevlcEW2xhHweVi14wQT2rWtMAFkI%3D%0A%0A"
        val postData: ByteArray = message.toByteArray(StandardCharsets.UTF_8)

        connection.setRequestProperty("charset", "utf-8")
        connection.setRequestProperty("Content-length", postData.size.toString())
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

        try {
            val outputStream: DataOutputStream = DataOutputStream(connection.outputStream)
            outputStream.write(postData)
            outputStream.flush()
        } catch (exception: Exception) {
            println(exception.message)
        }

        if (connection.responseCode != HttpsURLConnection.HTTP_OK && connection.responseCode != HttpsURLConnection.HTTP_CREATED) {
            try {
                val reader: BufferedReader = BufferedReader(InputStreamReader(connection.getInputStream()))
                val output: String = reader.readLine()

                println("There was error while connecting the chat $output")
                return false

            } catch (exception: Exception) {
                throw Exception("Exception while push the notification  $exception.message")
            }
        } else {
            try {
                //val reader: BufferedReader = BufferedReader(InputStreamReader(connection.getInputStream()))
                val output: String = connection.getInputStream().bufferedReader().use(BufferedReader::readText)

                println(output)
                return false;

            } catch (exception: Exception) {
                throw Exception("Exception while push the notification  $exception.message")
            }
        }

    }

    // Signs over a transaction if the specified Alive response for a particular SSN is correct
    // This function takes a filtered transaction which is a partial Merkle tree. Any parts of the transaction which
    // the oracle doesn't need to see in order to verify the correctness of the Alive response have been removed. In this
    // case, all but the [HumanLifeContract.Create] commands have been removed. If the Alive response is correct then the oracle
    // signs over the Merkle root (the hash) of the transaction.
    fun sign(ftx: FilteredTransaction): TransactionSignature {
        // Check the partial Merkle tree is valid.
        ftx.verify()

        /** Returns true if the component is an Create command that:
         *  - States the correct Alive response
         *  - Has the oracle listed as a signer
         */
        fun isCommandWithCorrectAliveResponseAndIAmSigner(elem: Any) = when {
            elem is Command<*> && elem.value is HumanLifeContract.Create -> {
                val cmdData = elem.value as HumanLifeContract.Create
                myKey in elem.signers && query(cmdData.ssn) == cmdData.alive
            }
            else -> false
        }

        // Is it a Merkle tree we are willing to sign over?
        val isValidMerkleTree = ftx.checkWithFun(::isCommandWithCorrectAliveResponseAndIAmSigner)

        if (isValidMerkleTree) {
            return services.createSignature(ftx, myKey)
        } else {
            throw IllegalArgumentException("Oracle signature requested over invalid transaction.")
        }
    }
}