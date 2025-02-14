// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.secret.aws;

import ai.vespa.secret.config.aws.AsmSecretConfig;
import ai.vespa.secret.config.aws.AsmTenantSecretConfig;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.VaultId;
import ai.vespa.secret.model.VaultName;
import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.athenz.identity.ServiceIdentityProvider;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Secret reader for tenant nodes.
 *
 * @author gjoranv
 */
public final class AsmTenantSecretReader extends AsmSecretReader {

    private final String system;
    private final String tenant;
    private final Map<VaultName, VaultId> vaultIds;

    @Inject
    public AsmTenantSecretReader(AsmSecretConfig secretConfig,
                                 AsmTenantSecretConfig tenantConfig,
                                 ServiceIdentityProvider identities) {
        super(secretConfig, identities);
        this.system = tenantConfig.system();
        this.tenant = tenantConfig.tenant();
        this.vaultIds = createVaultIdMap(tenantConfig);
    }

    // For testing
    AsmTenantSecretReader(Function<AwsRolePath, SecretsManagerClient> clientAndCredentialsSupplier,
                          String system, String tenant, Map<VaultName, VaultId> vaultIds) {
        super(clientAndCredentialsSupplier);
        this.system = system;
        this.tenant = tenant;
        this.vaultIds = vaultIds;
    }

    static Map<VaultName, VaultId> createVaultIdMap(AsmTenantSecretConfig config) {
        // Note: we can rightfully assume that the vaults are unique by name for a tenant.
        return config.vaults().stream()
                .map(vault -> Map.entry(VaultName.of(vault.name()), VaultId.of(vault.id())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    protected AwsRolePath awsRole(VaultName vault) {
        return AthenzUtil.awsReaderRole(system, tenant, vault);
    }

    @Override
    protected String awsSecretId(Key key) {
        return awsSecretId(tenant, key);
    }

    // Note: TenantName cannot be used here, as config-provisioning is not available on tenant nodes.

    private String awsSecretId(String tenant, Key key) {
        return getAwsSecretId(system, tenant, key);
    }

    public static String getAwsSecretId(String system, String tenant, Key key) {
        return "%s.%s.%s.%s/%s".formatted(AthenzUtil.PREFIX, system, tenant,
                                          key.vaultName().value(), key.secretName().value());
    }

}
