package org.adridadou.ethereum.smartcontract;

import com.google.common.collect.Lists;
import org.adridadou.ethereum.BlockchainProxyRpc;
import org.adridadou.ethereum.EthAddress;
import org.adridadou.exception.EthereumApiException;
import org.ethereum.core.*;
import org.ethereum.core.CallTransaction.Contract;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.spongycastle.util.encoders.Hex;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import rx.Observable;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Created by davidroon on 20.04.16.
 * This code is released under Apache 2 license
 */
public class SmartContractRpc implements SmartContract {
    private EthAddress address;
    private Contract contract;
    private final Web3j web3j;
    private final BlockchainProxyRpc bcProxy;
    private final ECKey sender;
    private final EthAddress senderAddress;

    public SmartContractRpc(String abi, Web3j web3j, ECKey sender, EthAddress address, BlockchainProxyRpc bcProxy) {
        this.contract = new Contract(abi);
        this.web3j = web3j;
        this.sender = sender;
        this.bcProxy = bcProxy;
        this.address = address;
        this.senderAddress = EthAddress.of(sender.getAddress());
    }

    public List<CallTransaction.Function> getFunctions() {
        return Lists.newArrayList(contract.functions);
    }

    public Object[] callConstFunction(String functionName, Object... args) {

        Transaction tx = CallTransaction.createCallTransaction(0, 0, 100000000000000L,
                address.toString(), 0, contract.getByName(functionName), args);
        tx.sign(sender);

        try {
            org.web3j.protocol.core.methods.response.EthCall result = web3j
                    .ethCall(org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(senderAddress.toString(), //from
                            BigInteger.ZERO, //gas price
                            BigInteger.valueOf(100_000_000_000_000L), //gas limit
                            address.toString(), //to
                            BigInteger.ZERO, //value
                            Hex.toHexString(tx.getData())), DefaultBlockParameter.valueOf("latest")).send();
            return contract.getByName(functionName).decodeResult(Hex.decode(result.getResult()));
        } catch (IOException e) {
            throw new EthereumApiException("error while const calling a function");
        }

    }

    public Observable<Object[]> callFunction(String functionName, Object... args) {
        return callFunction(1, functionName, args);
    }

    public Observable<Object[]> callFunction(long value, String functionName, Object... args) {
        CallTransaction.Function func = contract.getByName(functionName);

        if (func == null) {
            throw new EthereumApiException("function " + functionName + " cannot be found. available:" + getAvailableFunctions());
        }
        byte[] functionCallBytes = func.encode(args);

        return bcProxy.sendTx(value, functionCallBytes, sender, address)
                .map(receipt -> Optional.ofNullable(receipt.getResult())
                        .map(result -> contract.getByName(functionName).decodeResult(result)).orElse(null));

    }

    private String getAvailableFunctions() {
        List<String> names = new ArrayList<>();
        for (CallTransaction.Function func : contract.functions) {
            names.add(func.name);
        }
        return names.toString();
    }

    public EthAddress getAddress() {
        return address;
    }
}
