using System;
using System.ServiceProcess;
using System.Diagnostics;

public class TigerSafeGuardianService : ServiceBase
{

    public static void Main()
    {
        System.ServiceProcess.ServiceBase.Run(new TigerSafeGuardianService());
    }

    public TigerSafeGuardianService()
    {
        ServiceName = "$gdnServName";
        CanStop = true;
        CanPauseAndContinue = false;
        AutoLog = true;
    }

    protected override void OnStart(string[] args)
    {
        Process p = new Process();
        p.StartInfo.UseShellExecute = false;
        p.StartInfo.CreateNoWindow = true;
        p.StartInfo.FileName = "$pwshPath";
        p.StartInfo.Arguments = "-ExecutionPolicy AllSigned -File \"$ps1GdnPath\"";
        p.Start();
    }

}