package org.p2p.solanaj.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bitcoinj.core.Base58;

import org.p2p.solanaj.utils.ByteUtils;
import org.p2p.solanaj.utils.ShortvecDecoding;
import org.p2p.solanaj.utils.ShortvecEncoding;

public class Message {

    private static class MessageHeader {
        static final int HEADER_LENGTH = 3;

        byte numRequiredSignatures = 0;
        byte numReadonlySignedAccounts = 0;
        byte numReadonlyUnsignedAccounts = 0;

        byte[] toByteArray() {
            return new byte[] { numRequiredSignatures, numReadonlySignedAccounts, numReadonlyUnsignedAccounts };
        }
    }

    private static class CompiledInstruction {
        byte programIdIndex;
        byte[] keyIndicesCount;
        byte[] keyIndices;
        byte[] dataLength;
        byte[] data;

        int getLength() {
            // 1 = programIdIndex length
            return 1 + keyIndicesCount.length + keyIndices.length + dataLength.length + data.length;
        }
    }


    private static final int SIGNATURE_LENGTH = 64;
    private static final int PUBKEY_LENGTH = 32;
    private static final int RECENT_BLOCK_HASH_LENGTH = 32;

    private MessageHeader messageHeader;
    private String recentBlockhash;
    private AccountKeysList accountKeys;
    private List<TransactionInstruction> instructions;
    private PublicKey feePayer;

    public Message() {
        this.accountKeys = new AccountKeysList();
        this.instructions = new ArrayList<TransactionInstruction>();
    }

    public Message(AccountKeysList accountKeys, List<TransactionInstruction> instructions) {
        this.accountKeys = accountKeys;
        this.instructions = instructions;
    }

    public Message addInstruction(TransactionInstruction instruction) {
        accountKeys.addAll(instruction.getKeys());
        accountKeys.add(new AccountMeta(instruction.getProgramId(), false, false));
        instructions.add(instruction);

        return this;
    }

    public TransactionInstruction getInstruction(int index) {
        return instructions.get(index);
    }

    public int getInstructionCount() {
        return instructions.size();
    }

    public void setRecentBlockHash(String recentBlockhash) {
        this.recentBlockhash = recentBlockhash;
    }

    public String getRecentBlockHash() {
        return recentBlockhash;
    }

    public PublicKey getFeePayer() {
        return feePayer;
    }

    private static AccountMeta keyToAccountMeta(
            int keyIndex,
            List<PublicKey> keys,
            int numRequiredSignatures,
            int numReadonlySignedAccounts,
            int numReadonlyUnsignedAccounts
    ) {
        boolean isSigner = keyIndex < numRequiredSignatures;
        boolean isWriteable = (
                keyIndex < (numRequiredSignatures - numReadonlySignedAccounts) ||
                (keyIndex >= numRequiredSignatures &&
                        keyIndex < (keys.size() - numReadonlyUnsignedAccounts))
        );

        return new AccountMeta(keys.get(keyIndex), isSigner, isWriteable);
    }

    private static TransactionInstruction parseInstruction(
            ByteArrayInputStream messageBytes,
            List<PublicKey> keys,
            int numRequiredSignatures,
            int numReadonlySignedAccounts,
            int numReadonlyUnsignedAccounts) throws IOException {
        byte programIdIndex = (byte) messageBytes.read();
        byte accountCount = (byte) ShortvecDecoding.decodeLength(messageBytes);
        byte[] accounts = messageBytes.readNBytes(accountCount);
        byte dataLength = (byte) ShortvecDecoding.decodeLength(messageBytes);
        byte[] data = messageBytes.readNBytes(dataLength);

        PublicKey programId = keys.get(programIdIndex);

        List<AccountMeta> accountMetaList = IntStream
                .range(0, accountCount)
                .mapToObj(i -> Message.keyToAccountMeta(
                        accounts[i],
                        keys,
                        numRequiredSignatures,
                        numReadonlySignedAccounts,
                        numReadonlyUnsignedAccounts)
                )
                .collect(Collectors.toList());

        return new TransactionInstruction(programId, accountMetaList, data);
    }

    public static Message from(ByteArrayInputStream messageBytes) {
        int numRequiredSignatures = messageBytes.read();
        int numReadonlySignedAccounts = messageBytes.read();
        int numReadonlyUnsignedAccounts = messageBytes.read();

        int accountCount = ShortvecDecoding.decodeLength(messageBytes);

        List<PublicKey> accountKeys = IntStream
                .range(0, accountCount)
                .mapToObj(i -> ByteUtils.readBytes(messageBytes, PUBKEY_LENGTH))
                .map(Base58::encode)
                .map(PublicKey::new)
                .collect(Collectors.toList());

        String recentBlockhash = Base58.encode(ByteUtils.readBytes(messageBytes, RECENT_BLOCK_HASH_LENGTH));

        int instructionCount = ShortvecDecoding.decodeLength(messageBytes);

        List<TransactionInstruction> instructions = IntStream
                .range(0, instructionCount)
                .mapToObj(i -> {
                    try {
                        return Message.parseInstruction(messageBytes, accountKeys, numRequiredSignatures, numReadonlySignedAccounts, numReadonlyUnsignedAccounts);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

        List<AccountMeta> accountMetas = accountKeys
                .stream()
                .map(key -> {
                    boolean isSigner = instructions.stream().anyMatch(
                            instruction -> instruction.getKeys().stream().anyMatch(
                                    accountMeta -> accountMeta.getPublicKey().equals(key) && accountMeta.isSigner()
                            ));

                    boolean isWriteable = instructions.stream().anyMatch(
                            instruction -> instruction.getKeys().stream().anyMatch(
                                    accountMeta -> accountMeta.getPublicKey().equals(key) && accountMeta.isWritable()
                            ));

                    return new AccountMeta(key, isSigner, isWriteable);
                })
                .collect(Collectors.toList());

        AccountKeysList accountKeysList = new AccountKeysList();
        accountKeysList.addAll(accountMetas);

        Message message = new Message(accountKeysList, instructions);

        message.setRecentBlockHash(recentBlockhash);

        if (accountKeys.size() > 0) {
            message.setFeePayer(accountKeys.get(0));
        }

        return message;
    }

    public byte[] serialize() {

        if (recentBlockhash == null) {
            throw new IllegalArgumentException("recentBlockhash required");
        }

        if (instructions.size() == 0) {
            throw new IllegalArgumentException("No instructions provided");
        }

        messageHeader = new MessageHeader();

        List<AccountMeta> keysList = getAccountKeys();
        int accountKeysSize = keysList.size();

        byte[] accountAddressesLength = ShortvecEncoding.encodeLength(accountKeysSize);

        int compiledInstructionsLength = 0;
        List<CompiledInstruction> compiledInstructions = new ArrayList<CompiledInstruction>();

        for (TransactionInstruction instruction : instructions) {
            int keysSize = instruction.getKeys().size();

            byte[] keyIndices = new byte[keysSize];
            for (int i = 0; i < keysSize; i++) {
                keyIndices[i] = (byte) findAccountIndex(keysList, instruction.getKeys().get(i).getPublicKey());
            }

            CompiledInstruction compiledInstruction = new CompiledInstruction();
            compiledInstruction.programIdIndex = (byte) findAccountIndex(keysList, instruction.getProgramId());
            compiledInstruction.keyIndicesCount = ShortvecEncoding.encodeLength(keysSize);
            compiledInstruction.keyIndices = keyIndices;
            compiledInstruction.dataLength = ShortvecEncoding.encodeLength(instruction.getData().length);
            compiledInstruction.data = instruction.getData();

            compiledInstructions.add(compiledInstruction);

            compiledInstructionsLength += compiledInstruction.getLength();
        }

        byte[] instructionsLength = ShortvecEncoding.encodeLength(compiledInstructions.size());

        int bufferSize = MessageHeader.HEADER_LENGTH + RECENT_BLOCK_HASH_LENGTH + accountAddressesLength.length
                + (accountKeysSize * PublicKey.PUBLIC_KEY_LENGTH) + instructionsLength.length
                + compiledInstructionsLength;

        ByteBuffer out = ByteBuffer.allocate(bufferSize);

        ByteBuffer accountKeysBuff = ByteBuffer.allocate(accountKeysSize * PublicKey.PUBLIC_KEY_LENGTH);
        for (AccountMeta accountMeta : keysList) {
            accountKeysBuff.put(accountMeta.getPublicKey().toByteArray());

            if (accountMeta.isSigner()) {
                messageHeader.numRequiredSignatures += 1;
                if (!accountMeta.isWritable()) {
                    messageHeader.numReadonlySignedAccounts += 1;
                }
            } else {
                if (!accountMeta.isWritable()) {
                    messageHeader.numReadonlyUnsignedAccounts += 1;
                }
            }
        }

        out.put(messageHeader.toByteArray());

        out.put(accountAddressesLength);
        out.put(accountKeysBuff.array());

        out.put(Base58.decode(recentBlockhash));

        out.put(instructionsLength);
        for (CompiledInstruction compiledInstruction : compiledInstructions) {
            out.put(compiledInstruction.programIdIndex);
            out.put(compiledInstruction.keyIndicesCount);
            out.put(compiledInstruction.keyIndices);
            out.put(compiledInstruction.dataLength);
            out.put(compiledInstruction.data);
        }

        return out.array();
    }

    protected void setFeePayer(PublicKey feePayer) {
        this.feePayer = feePayer;
    }

    public List<AccountMeta> getAccountKeys() {
        List<AccountMeta> keysList = accountKeys.getList();
        int feePayerIndex = findAccountIndex(keysList, feePayer);

        if (feePayerIndex == -1) {
            keysList.add(new AccountMeta(feePayer, true, true));
        }

        List<AccountMeta> newList = new ArrayList<AccountMeta>();
        AccountMeta feePayerMeta = keysList.get(feePayerIndex);
        newList.add(new AccountMeta(feePayerMeta.getPublicKey(), true, true));
        keysList.remove(feePayerIndex);
        newList.addAll(keysList);

        return newList;
    }

    private int findAccountIndex(List<AccountMeta> accountMetaList, PublicKey key) {
        for (int i = 0; i < accountMetaList.size(); i++) {
            if (accountMetaList.get(i).getPublicKey().equals(key)) {
                return i;
            }
        }

        return -1;
    }
}
