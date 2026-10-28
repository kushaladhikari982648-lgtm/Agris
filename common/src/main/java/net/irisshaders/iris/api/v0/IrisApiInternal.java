package net.irisshaders.iris.api.v0;

import net.irisshaders.iris.apiimpl.IrisApiV0Impl;

/**
 * Internal holder backing {@link IrisApi#getInstance()}.
 */
public final class IrisApiInternal {
    static final IrisApi INSTANCE = IrisApiV0Impl.INSTANCE;

    private IrisApiInternal() {
    }
}
