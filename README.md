![logo](https://orig08.deviantart.net/cbc5/f/2016/069/1/c/the_rat_pack_mischieficons_wharf_by_pandemoniumfire-d9ul2th.png)

# Ratatxt Android Client
Android client for Ratatxt. Serves as SMS receiver and sender with Ratatxt.

http://the-rat-packs.deviantart.com/ reference icon but NOT final (not sure if the artist will let me use it. not contacted yet)

## Supports
- Android Kitkat 4.4 ✔
- Android Noughat 7.1 ✔

## Specifications
- user login
    - allow user to login via email and password. ✔
- user logout
    - clear saved API tokens and settings. ✔
    - stop all services. ✔
- device selection
    - ability to select created devices from API. ✔
    - force user to select device after login. ✔
- sms receiver
    - SMS received from device pushes to API. ✔
    - allow user to manually start and stop the service. ✔
    - checks SMS receiving permissions before starting service. ✔
- sms sender
    - device received outbox from API to send as SMS. ✔
    - allow user to manually start and stop the service. ✔
    - checks SMS sending permissions before starting service. ✔
- app info
    - client version ✔
    - api version ✔
- device stats
    - outbox received ✔
    - SMS send ok ✔
    - SMS send fail ✔
    - SMS received ✔
    - inbox push ok ✔
    - inbox push fail ✔
- *device hearbeat
    - checks device availability by sending ping every minute.
    - record last device pong.
