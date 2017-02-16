package org.sputnikdev.bluetooth.manager;

import java.util.Date;

public interface AdapterListener {

    void powered(boolean powered);

    void discovering(boolean discovering);

    void lastUpdatedChanged(Date lastActivity);

}
