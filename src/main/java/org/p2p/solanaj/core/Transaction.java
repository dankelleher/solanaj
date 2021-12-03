package org.p2p.solanaj.core;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.utils.ByteUtils;
import org.p2p.solanaj.utils.ShortvecDecoding;
import org.p2p.solanaj.utils.ShortvecEncoding;
import org.p2p.solanaj.utils.TweetNaclFast;

public class Transaction {

    public static final int SIGNATURE_LENGTH = 64;

    private Message message;
    private List<String> signatures;
    private byte[] serializedMessage;

    public Transaction() {
        this.message = new Message();
        this.signatures = new ArrayList<String>();
    }

    public Transaction(Message message, List<String> signatures) {
        this.message = message;
        this.signatures = signatures;
    }

    public Transaction addInstruction(TransactionInstruction instruction) {
        message.addInstruction(instruction);

        return this;
    }

    public TransactionInstruction getInstruction(int index) {
        return message.getInstruction(index);
    }

    public int getInstructionCount() {
        return message.getInstructionCount();
    }

    public void setRecentBlockHash(String recentBlockhash) {
        message.setRecentBlockHash(recentBlockhash);
    }

    public String getRecentBlockHash() {
        return message.getRecentBlockHash();
    }

    public void sign(Account signer) {
        sign(Arrays.asList(signer));
    }

    public PublicKey getFeePayer() {
        return message.getFeePayer();
    }

    public List<String> getSignatures() {
        return signatures;
    }

    public Message getMessage() {
        return message;
    }

    public byte[] getSerializedMessage() {
        return serializedMessage;
    }

    void setSerializedMessage(byte[] serializedMessage) {
        this.serializedMessage = serializedMessage;
    }

    public void setFeePayer(PublicKey feePayer) {
        message.setFeePayer(feePayer);
    }

    public void sign(List<Account> signers) {

        if (signers.size() == 0) {
            throw new IllegalArgumentException("No signers");
        }

        Account feePayer = signers.get(0);
        message.setFeePayer(feePayer.getPublicKey());

        signSerializedMessage(signers);
    }

    public void signSerializedMessage(List<Account> signers) {
        if (serializedMessage == null) {
            serializedMessage = message.serialize();
        }

        for (Account signer : signers) {
            TweetNaclFast.Signature signatureProvider = new TweetNaclFast.Signature(new byte[0], signer.getSecretKey());
            byte[] signature = signatureProvider.detached(serializedMessage);

            signatures.add(Base58.encode(signature));
        }
    }

    public static Transaction from(String transactionString) {
        ByteArrayInputStream transactionBytes = new ByteArrayInputStream(Base64.getDecoder().decode(transactionString));

        int signatureCount = ShortvecDecoding.decodeLength(transactionBytes);

        List<String> signatures = IntStream
                .range(0, signatureCount)
                .mapToObj(i -> ByteUtils.readBytes(transactionBytes, SIGNATURE_LENGTH))
                .map(Base58::encode)
                .collect(Collectors.toList());

        // set the message serialized bytes
        // so that it is not recreated (which would invalidate previous signatures)
        byte[] serializedMessage = ByteUtils.readBytes(transactionBytes, transactionBytes.available());
        ByteArrayInputStream messageBytes = new ByteArrayInputStream(serializedMessage);

        Message message = Message.from(messageBytes);

        Transaction transaction = new Transaction(message, signatures);
        transaction.setSerializedMessage(serializedMessage);

        return transaction;
    }

    public byte[] serialize() {
        int signaturesSize = signatures.size();
        byte[] signaturesLength = ShortvecEncoding.encodeLength(signaturesSize);

        ByteBuffer out = ByteBuffer
                .allocate(signaturesLength.length + signaturesSize * SIGNATURE_LENGTH + serializedMessage.length);

        out.put(signaturesLength);

        for (String signature : signatures) {
            byte[] rawSignature = Base58.decode(signature);
            out.put(rawSignature);
        }

        out.put(serializedMessage);

        return out.array();
    }
}
