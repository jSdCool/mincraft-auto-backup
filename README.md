# automatic world backup for minecraft

automatically backs up your Minecraft server

NOTE: This is a server side only mod, it will not work on the client

## setup
After installing the mod on your server:
1. Launch the server
2. Stop the server
3. Edit config/backup.cfg
4. Set the backup destination to the folder path where backups should be placed
5. Set the hours between backups to your desired values
6. Set compression to your desired value from the list below
7. Set enabled to true
8. Save the config file
9. Launch the server

## Compression
We offer the following options for compression:  
- NONE  
- ZIP
- GZIP
- LZ4
- XZ
- LZMA

Here are some tested statistics for each one:  

| Method |      Time | Compressed Size |
|--------|----------:|:---------------:|
| NONE   |    3098ms |     1.62 GB     |
| ZIP    |   53276ms |     1.42 GB     |
| GZIP   |   50125ms |     1.42 GB     |
| LZ4    |    3293ms |     1.43 GB     |
| XZ     |  782807ms |    1.3597 GB    |
| LZMA   | 3455693ms |    1.3595 GB    |



