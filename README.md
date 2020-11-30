# MI-lite
A light version of MobileInsight monitor with limited functionality

militelibrary is the module which wraps an Android Service named MILiteService. It provides basic functions to dump diag file from diagnostic port to external storage. Check the app module to see how to launch the MILiteService.

## Required Permission
- READ_EXTERNAL_STORAGE
- WRITE_EXTERNAL_STORAGE

### Suggested
- FOREGROUND_SERVICE

## Exposed Interface
#### Set/get output path in external storage directory:
* public boolean setOutputPath(String path)
* public String getOutputPath()

#### Set/get output diag file cut file size in MB:
* public boolean setCutSize(int cutSize)
* public int getCutSize()

#### Set/get config option:
DiagConfig options:

    public enum DiagConfig {
        all,
        lte_3g_control,
        lte_control,
        lte_control_data,
        lte_control_data_phy,
        lte_control_phy,
        lte_phy,
        suggested,
        customized,
    }
* public boolean setDiagConfigOption(DiagConfig config)
* public boolean setDiagConfigOption(DiagConfig config, File configFile)
* public DiagConfig getDiagConfigOption()

#### Start/stop collection:
* public boolean start()
* public void stop()

#### Insert customized message:
* public void insertCustomMsg(String strMsg)

