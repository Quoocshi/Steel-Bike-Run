# 🔐 GitHub Secrets cần thiết cho CI/CD Pipeline

Truy cập: **GitHub Repo → Settings → Secrets and variables → Actions → New repository secret**

| Secret Name        | Giá trị                              | Ghi chú                            |
|--------------------|--------------------------------------|------------------------------------|
| `JWT_SECRET`       | `h3-saferide-super-secret-key-...`  | Tối thiểu 32 ký tự                 |
| `JWT_EXPIRATION_MS`| `3600000`                           | 1 giờ = 3600000ms                  |
| `DB_URL`           | `jdbc:postgresql://ep-falling...`   | Full JDBC URL của Neon              |
| `DB_USERNAME`      | `neondb_owner`                      | Username Neon PostgreSQL            |
| `DB_PASSWORD`      | `npg_ChbTjscM73Sx`                  | Password Neon PostgreSQL            |

> ⚠️ **Lưu ý bảo mật**: Không bao giờ commit file `application.properties` 
> có chứa thông tin thật lên repository. File này đã được thêm vào `.gitignore`.
