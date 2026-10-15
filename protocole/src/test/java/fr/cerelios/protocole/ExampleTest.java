package fr.cerelios.protocole;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import fr.cerelios.protocole.messages.Example;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExampleTest {

    @Test
    void serializeAndDeserializeMessage() throws Exception {
        BigInteger number = BigInteger.TWO.pow(2048).subtract(BigInteger.ONE);
        Example message =
                Example.newBuilder()
                        .setText("Hello world \uD83D\uDE00")
                        .setBigInt(ByteString.copyFrom(number.toByteArray()))
                        .build();

        Example recu = Example.parseFrom(message.toByteArray());

        assertEquals("Hello world \uD83D\uDE00", recu.getText());
        assertEquals(number, new BigInteger(recu.getBigInt().toByteArray()));
    }
}
