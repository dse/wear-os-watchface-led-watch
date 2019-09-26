package com.webonastick.watchface;

public interface MultiTapEventHandler<RegionType> {
    public void onMultiTapCommand(RegionType region, int numberOfTaps);
}
