using System;
using System.Runtime.InteropServices;

public class TigerSafeProcPerms
{
    [DllImport("advapi32.dll", SetLastError = true)]
    public static extern uint SetSecurityInfo(IntPtr handle, int ObjectType, uint SecurityInfo, IntPtr pSidOwner, IntPtr pSidGroup, IntPtr pDacl, IntPtr pSacl);

    [DllImport("advapi32.dll", SetLastError = true)]
    public static extern bool ConvertStringSecurityDescriptorToSecurityDescriptor(string StringSecurityDescriptor, uint StringSDRevision, out IntPtr pSecurityDescriptor, out uint SecurityDescriptorSize);

    [DllImport("advapi32.dll", SetLastError = true)]
    public static extern bool GetSecurityDescriptorDacl(IntPtr pSecurityDescriptor, out bool bDaclPresent, out IntPtr pDacl, out bool bDaclDefaulted);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern IntPtr OpenProcess(uint dwDesiredAccess, bool bInheritHandle, int dwProcessId);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr hObject);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern IntPtr LocalFree(IntPtr hMem);

    public const uint PROCESS_ALL_ACCESS = 0x1F0FFF;
    public const uint DACL_SECURITY_INFORMATION = 0x00000004;
    public const uint SDDL_REVISION_1 = 1;
    public const int SE_KERNEL_OBJECT = 6;

    public static void SetProcessSDDL(int processId, string toSet)
    {
        IntPtr hProcess = OpenProcess(PROCESS_ALL_ACCESS, false, processId);
        if (hProcess == IntPtr.Zero)
        {
            throw new Exception("Failed to open process " + processId);
        }

        try
        {
            IntPtr pSecurityDescriptor;
            uint securityDescriptorSize;
            if (!ConvertStringSecurityDescriptorToSecurityDescriptor(toSet, SDDL_REVISION_1, out pSecurityDescriptor, out securityDescriptorSize))
            {
                int errId = Marshal.GetLastWin32Error();
                throw new Exception("Failed to ConvertStringSecurityDescriptorToSecurityDescriptor: " + errId);
            }

            try
            {
                bool bDaclPresent;
                IntPtr pDacl;
                bool bDaclDefaulted;
                if (!GetSecurityDescriptorDacl(pSecurityDescriptor, out bDaclPresent, out pDacl, out bDaclDefaulted))
                {
                    int errId = Marshal.GetLastWin32Error();
                    throw new Exception("Failed to GetSecurityDescriptorDacl: " + errId);
                }

                if (!bDaclPresent || pDacl == IntPtr.Zero)
                {
                    throw new Exception("Failed to get bDacl.");
                }

                uint res = SetSecurityInfo(hProcess, SE_KERNEL_OBJECT, DACL_SECURITY_INFORMATION, IntPtr.Zero, IntPtr.Zero, pDacl, IntPtr.Zero);
                if (res != 0)
                {
                    throw new Exception("Failed to SetSecurityInfo: " + res);
                }
            }
            finally
            {
                LocalFree(pSecurityDescriptor);
            }
        }
        finally
        {
            CloseHandle(hProcess);
        }
    }
}