package com.flowa.app

import android.app.Application
import com.flowa.data.*
import com.flowa.domain.RunWorkflow

class FlowaApplication : Application() {
    val repository by lazy { LocalRepository(this) }
    val permissions by lazy { DevicePermissions(this) }
    val deviceTools by lazy { DeviceToolEngine(this, permissions) }
    val ai by lazy { HttpAiGateway(repository, deviceTools) }
    val device by lazy { AndroidDeviceGateway(this) }
    val discovery by lazy { DeviceDiscovery(this) }
    val runWorkflow by lazy { RunWorkflow(repository, ai, device) }
}
