package ai.lotus.app.shizuku;

interface ILotusShizukuService {
    // Shizuku server 保留的销毁 transaction。
    void destroy() = 16777114;

    // requestJson: {"tool":"device.status","args":{...}}
    String execute(String requestJson) = 1;

    // 连接后立刻校验当前 UserService 的真实身份。
    String status() = 2;
}
