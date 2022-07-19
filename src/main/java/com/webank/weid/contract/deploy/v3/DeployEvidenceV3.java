

package com.webank.weid.contract.deploy.v3;

import com.webank.weid.config.FiscoConfig;
import com.webank.weid.constant.CnsType;
import com.webank.weid.constant.WeIdConstant;
import com.webank.weid.contract.deploy.AddressProcess;
import com.webank.weid.contract.v3.EvidenceContract;
import com.webank.weid.protocol.base.WeIdPrivateKey;
import com.webank.weid.service.BaseService;
import com.webank.weid.util.DataToolUtils;
import java.math.BigInteger;
import org.apache.commons.lang3.StringUtils;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.utils.Numeric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeployEvidenceV3 extends AddressProcess {
    
    /**
     * log4j.
     */
    private static final Logger logger = LoggerFactory.getLogger(
        DeployEvidenceV3.class);

    /**
     * The cryptoKeyPair.
     */
    private static CryptoKeyPair cryptoKeyPair;
    
    /**
     * Inits the cryptoKeyPair.
     *
     * @return true, if successful
     */
    private static String initCryptoKeyPair(String inputPrivateKey) {
        if (StringUtils.isNotBlank(inputPrivateKey)) {
            logger.info("[DeployEvidenceV2] begin to init credentials by privateKey..");
            cryptoKeyPair = ((Client) BaseService.getClient()).getCryptoSuite()
                .getKeyPairFactory().createKeyPair(new BigInteger(inputPrivateKey));
        } else {
            // 此分支逻辑实际情况不会执行，因为通过build-tool进来是先给创建私钥
            logger.info("[DeployEvidenceV2] begin to init credentials..");
            /*credentials = GenCredential.create();
            String privateKey = credentials.getEcKeyPair().getPrivateKey().toString();
            String publicKey = credentials.getEcKeyPair().getPublicKey().toString();*/
            cryptoKeyPair = ((Client) BaseService.getClient()).getCryptoSuite()
                .getKeyPairFactory().generateKeyPair();
            byte[] priBytes = Numeric.hexStringToByteArray(cryptoKeyPair.getHexPrivateKey());
            byte[] pubBytes = Numeric.hexStringToByteArray(cryptoKeyPair.getHexPublicKey());
            String privateKey = new BigInteger(1, priBytes).toString(10);
            String publicKey = new BigInteger(1, pubBytes).toString(10);
            writeAddressToFile(publicKey, "ecdsa_key.pub");
            writeAddressToFile(privateKey, "ecdsa_key");
        }

        //if (credentials == null) {
        if (cryptoKeyPair == null) {
            logger.error("[DeployEvidenceV2] credentials init failed. ");
            return StringUtils.EMPTY;
        }
        //return credentials.getEcKeyPair().getPrivateKey().toString();
        byte[] priBytes = Numeric.hexStringToByteArray(cryptoKeyPair.getHexPrivateKey());
        return new BigInteger(1, priBytes).toString(10);
    }

    protected static Client getClient(String groupId) {
        return (Client) BaseService.getClient(groupId);
    }
    
    public static String deployContract(
        FiscoConfig fiscoConfig,
        String inputPrivateKey,
        String groupId,
        boolean instantEnable
    ) {
        //String privateKey = initCredentials(inputPrivateKey);
        String privateKey = initCryptoKeyPair(inputPrivateKey);
        // 构建私钥对象
        WeIdPrivateKey weIdPrivateKey = new WeIdPrivateKey();
        weIdPrivateKey.setPrivateKey(privateKey);
        
        String evidenceAddress = deployEvidenceContractsNew(groupId);
        // 将地址注册到cns中
        CnsType cnsType = CnsType.SHARE;
        RegisterAddressV3.registerAllCns(weIdPrivateKey);
        // 根据群组和evidence Address获取hash
        String hash = getHashForShare(groupId, evidenceAddress);
        // 将evidence地址注册到cns中
        RegisterAddressV3.registerAddress(
            cnsType, 
            hash, 
            evidenceAddress, 
            WeIdConstant.CNS_EVIDENCE_ADDRESS, 
            weIdPrivateKey
        );
        // 将群组编号注册到cns中
        RegisterAddressV3.registerAddress(
            cnsType, 
            hash, 
            groupId.toString(), 
            WeIdConstant.CNS_GROUP_ID, 
            weIdPrivateKey
        );
        
        if (instantEnable) {
            //将evidence hash配置到机构配置cns中
            RegisterAddressV3.registerHashToOrgConfig(
                fiscoConfig.getCurrentOrgId(), 
                WeIdConstant.CNS_EVIDENCE_HASH + groupId.toString(), 
                hash, 
                weIdPrivateKey
            );
            //将evidence地址配置到机构配置cns中
            RegisterAddressV3.registerHashToOrgConfig(
                fiscoConfig.getCurrentOrgId(), 
                WeIdConstant.CNS_EVIDENCE_ADDRESS + groupId.toString(), 
                evidenceAddress, 
                weIdPrivateKey
            );
            // 合约上也启用hash
            RegisterAddressV3.enableHash(cnsType, hash, weIdPrivateKey);
        }
        return hash;
    }
    
    private static String deployEvidenceContractsNew(String groupId) {
        try {
            EvidenceContract evidenceContract =
                EvidenceContract.deploy(
                    getClient(groupId),
                    cryptoKeyPair
                );
            String evidenceContractAddress = evidenceContract.getContractAddress();
            return evidenceContractAddress;
        } catch (Exception e) {
            logger.error("EvidenceFactory deploy exception", e);
        }
        return StringUtils.EMPTY;
    }
}
