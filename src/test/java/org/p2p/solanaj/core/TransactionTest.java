package org.p2p.solanaj.core;

import org.p2p.solanaj.programs.MemoProgram;
import org.p2p.solanaj.programs.SystemProgram;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.bitcoinj.core.Base58;

public class TransactionTest {

    public static final String RECENT_BLOCKHASH = "Eit7RCyhUixAe2hGBS8oqnw59QK3kgMMjfLME5bm9wRn";
    public static final String MEMO = "Test memo";

    private final static Account signer = new Account(Base58
            .decode("4Z7cXSyeFR8wNGMVXUE1TwtKn5D5Vu7FzEv69dokLv7KrQk7h6pu4LF8ZRR9yQBhc7uSM6RTTZtU1fmaxiNrxXrs"));

    @Test
    public void signAndSerialize() {
        PublicKey fromPublicKey = new PublicKey("QqCCvshxtqMAL2CVALqiJB7uEeE5mjSPsseQdDzsRUo");
        PublicKey toPublickKey = new PublicKey("GrDMoeqMLFjeXQ24H56S1RLgT4R76jsuWCd6SvXyGPQ5");
        int lamports = 3000;

        Transaction transaction = new Transaction();
        transaction.addInstruction(SystemProgram.transfer(fromPublicKey, toPublickKey, lamports));
        transaction.setRecentBlockHash("Eit7RCyhUixAe2hGBS8oqnw59QK3kgMMjfLME5bm9wRn");
        transaction.sign(signer);
        byte[] serializedTransaction = transaction.serialize();

        assertEquals(
                "ASdDdWBaKXVRA+6flVFiZokic9gK0+r1JWgwGg/GJAkLSreYrGF4rbTCXNJvyut6K6hupJtm72GztLbWNmRF1Q4BAAEDBhrZ0FOHFUhTft4+JhhJo9+3/QL6vHWyI8jkatuFPQzrerzQ2HXrwm2hsYGjM5s+8qMWlbt6vbxngnO8rc3lqgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAy+KIwZmU8DLmYglP3bPzrlpDaKkGu6VIJJwTOYQmRfUBAgIAAQwCAAAAuAsAAAAAAAA=",
                Base64.getEncoder().encodeToString(serializedTransaction));
    }

    @Test
    public void transactionBuilderTest() {
        final Transaction transaction = new TransactionBuilder()
                .addInstruction(
                        MemoProgram.writeUtf8(
                                signer.getPublicKey(),
                                MEMO
                        )
                )
                .setRecentBlockHash(RECENT_BLOCKHASH)
                .setSigners(List.of(signer))
                .build();

        assertEquals(
                "AV6w4Af9PSHhNsTSal4vlPF7Su9QXgCVyfDChHImJITLcS5BlNotKFeMoGw87VwjS3eNA2JCL+MEoReynCNbWAoBAAECBhrZ0FOHFUhTft4+JhhJo9+3/QL6vHWyI8jkatuFPQwFSlNQ+F3IgtYUpVZyeIopbd8eq6vQpgZ4iEky9O72oMviiMGZlPAy5mIJT92z865aQ2ipBrulSCScEzmEJkX1AQEBAAlUZXN0IG1lbW8=",
                Base64.getEncoder().encodeToString(transaction.serialize())
        );
    }

    @Test
    public void deserializeTest() {
        String serializedTransaction = "AV6w4Af9PSHhNsTSal4vlPF7Su9QXgCVyfDChHImJITLcS5BlNotKFeMoGw87VwjS3eNA2JCL+MEoReynCNbWAoBAAECBhrZ0FOHFUhTft4+JhhJo9+3/QL6vHWyI8jkatuFPQwFSlNQ+F3IgtYUpVZyeIopbd8eq6vQpgZ4iEky9O72oMviiMGZlPAy5mIJT92z865aQ2ipBrulSCScEzmEJkX1AQEBAAlUZXN0IG1lbW8=";
        Transaction transaction = Transaction.from(serializedTransaction);

        assertThat(transaction.getInstructionCount(), is(1));
        assertThat(transaction.getRecentBlockHash(), is(RECENT_BLOCKHASH));
        assertThat(transaction.getFeePayer(), is(signer.getPublicKey()));

        TransactionInstruction memoInstruction = transaction.getInstruction(0);
        assertThat(memoInstruction.getProgramId(), is(MemoProgram.PROGRAM_ID));
        assertThat(memoInstruction.getData(), is(MEMO.getBytes(StandardCharsets.UTF_8)));
    }
}
