# Trial Database Verification

## Checklist

- [x] MySQL client is available.
- [x] Local MySQL connection succeeds.
- [x] MySQL server version recorded.
- [x] Development database availability checked.
- [x] No credentials committed to Git.

## Results

| Check                 | Command / evidence                            | Status  | Result                                                                                    |
| --------------------- | --------------------------------------------- | ------- | ----------------------------------------------------------------------------------------- |
| MySQL client          | `mysql --version` or full executable path     | PASS    | Client version 8.0.46 confirmed                                                           |
| Local connection      | `mysql -u root -p`                            | PASS    | Successfully logged in using `mysql -u root -p`                                           |
| Server version        | `SELECT VERSION();`                           | PASS    | returned `8.0.46`                                                                         |
| Development database  | `SHOW DATABASES;`                             | PASS    | executed successfully                                                                     |
| Git credentials check | `git status --short` and review tracked files | PARTIAL | `git grep` was run, but this alone does not prove no credentials have ever been committed |

## Blockers

- `mysql` was initially not recognized by PowerShell because its executable directory was not on PATH.
- The intended Online Bookstore development database has not yet been identified in the results.
- Git credential safety requires review of search matches and, if necessary, repository history.

## Conclusion

MySQL client and local server connectivity were verified successfully. The server reports version 8.0.46. The team must confirm the intended development database and finish the credential review before closing all verification items.

