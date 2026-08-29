UPDATE post
SET images = REPLACE(
        REPLACE(
            REPLACE(images,
                'https://objectstorage.ap-tokyo-1.oraclecloud.com/p/A0TR4wkNYrcGOzvpjsPLLgmD4y9YncSMik4XcQNwY7B5uo7BRt6K1qmEdFME9l27/n/nrrguvtqppqi/b/ortus-bucket/o/',
                ''),
            'https://objectstorage.ap-tokyo-1.oraclecloud.com/p/5R94ZnD93i9YTCorrhHEGgkXcgT2tu6J_BD46w3gCc3oJeUa-r-C82LOvxrDvMxE/n/nrrguvtqppqi/b/ortus-bucket/o/',
            ''),
        'https://objectstorage.ap-tokyo-1.oraclecloud.com/p/DrCIcuZzY23irWZEg-Z28KiNqiaYAhxmr9dHddU1uS-GuopaYi6TCQ7Ok7lRCU0C/n/nrrguvtqppqi/b/ortus-bucket/o/',
        '')
WHERE images LIKE '%objectstorage.ap-tokyo-1.oraclecloud.com%';

UPDATE post
SET content = REPLACE(
        REPLACE(content,
            'https://objectstorage.ap-tokyo-1.oraclecloud.com/p/A0TR4wkNYrcGOzvpjsPLLgmD4y9YncSMik4XcQNwY7B5uo7BRt6K1qmEdFME9l27/n/nrrguvtqppqi/b/ortus-bucket/o/',
            'https://objectstorage.ap-tokyo-1.oraclecloud.com/p/DrCIcuZzY23irWZEg-Z28KiNqiaYAhxmr9dHddU1uS-GuopaYi6TCQ7Ok7lRCU0C/n/nrrguvtqppqi/b/ortus-bucket/o/'),
        'https://objectstorage.ap-tokyo-1.oraclecloud.com/p/5R94ZnD93i9YTCorrhHEGgkXcgT2tu6J_BD46w3gCc3oJeUa-r-C82LOvxrDvMxE/n/nrrguvtqppqi/b/ortus-bucket/o/',
        'https://objectstorage.ap-tokyo-1.oraclecloud.com/p/DrCIcuZzY23irWZEg-Z28KiNqiaYAhxmr9dHddU1uS-GuopaYi6TCQ7Ok7lRCU0C/n/nrrguvtqppqi/b/ortus-bucket/o/')
WHERE content LIKE '%objectstorage.ap-tokyo-1.oraclecloud.com%';
