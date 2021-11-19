package com.amazon.aws.partners.saasfactory.saasboost;

import java.util.Collection;

public interface SettingsConvertible {
    public Collection<Setting> toSettings();
    public Collection<Setting> toEmptySettings();
    public void fillSettings(Collection<Setting> settings) throws IllegalArgumentException;
}

